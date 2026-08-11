package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.FormDtos.FormField;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.FileObject;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.InvoiceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InvoiceSubmissionService {
    private final InvoiceMapper invoiceMapper;
    private final FileObjectService fileService;

    public InvoiceSubmissionService(InvoiceMapper invoiceMapper, FileObjectService fileService) {
        this.invoiceMapper = invoiceMapper;
        this.fileService = fileService;
    }

    public void validateAndSubmit(String organizationId, String applicationId,
                                  String actorCasId, FormSchema schema, JsonNode answers) {
        List<Invoice> activeInvoices = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, organizationId)
                .eq(Invoice::getApplicationId, applicationId)
                .ne(Invoice::getStatus, "VOIDED"));
        Set<String> actualIds = new HashSet<>();
        for (Invoice invoice : activeInvoices) {
            actualIds.add(invoice.getId());
            if (!Set.of("DRAFT", "RETURNED").contains(invoice.getStatus())) {
                throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
            }
            validateAmount(invoice);
            fileService.requireReadyOwned(invoice.getCurrentFileId(), actorCasId,
                    Set.of("INVOICE_ORIGINAL"));
        }

        Set<String> referencedInvoiceIds = new HashSet<>();
        boolean hasInvoiceField = false;
        for (FormField field : schema.fields()) {
            JsonNode value = answers.get(field.key());
            if ("INVOICE".equals(field.type())) {
                hasInvoiceField = true;
                addReferences(value, referencedInvoiceIds);
            } else if ("ATTACHMENT".equals(field.type()) && value != null && value.isArray()) {
                for (JsonNode item : value) {
                    FileObject file = fileService.requireReadyOwned(item.asText(), actorCasId,
                            Set.of("FORM_ATTACHMENT", "OTHER"));
                    if (field.fileRule() != null && (file.getSizeBytes() > field.fileRule().maxSizeBytes()
                            || !field.fileRule().allowedContentTypes().contains(file.getContentType()))) {
                        throw new BusinessException(BizCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
                    }
                    fileService.markReferenced(file.getId());
                }
            }
        }
        if (hasInvoiceField && !referencedInvoiceIds.equals(actualIds)) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST,
                    "表单中的发票引用必须与当前申请发票一致");
        }
        for (Invoice invoice : activeInvoices) {
            long version = invoice.getVersion() == null ? 0 : invoice.getVersion();
            invoice.setStatus("SUBMITTED");
            invoice.setVersion(version);
            if (invoiceMapper.updateById(invoice) != 1) {
                throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
            }
        }
    }

    private void addReferences(JsonNode value, Set<String> target) {
        if (value == null || !value.isArray()) return;
        for (JsonNode item : value) target.add(item.asText());
    }

    private void validateAmount(Invoice invoice) {
        if (invoice.getFaceAmount() == null || invoice.getClaimedAmount() == null
                || invoice.getFaceAmount().signum() < 0 || invoice.getClaimedAmount().signum() <= 0
                || invoice.getClaimedAmount().compareTo(invoice.getFaceAmount()) > 0) {
            throw new BusinessException(BizCode.INVOICE_AMOUNT_INVALID, HttpStatus.BAD_REQUEST);
        }
    }
}
