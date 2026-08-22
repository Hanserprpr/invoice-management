package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.InvoiceDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.*;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class InvoiceService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED", "PENDING_RECOGNITION");
    private static final Set<String> ORIGINAL_PURPOSES = Set.of("INVOICE_ORIGINAL");

    private final InvoiceMapper invoiceMapper;
    private final InvoiceFileRevisionMapper revisionMapper;
    private final AttachmentMapper attachmentMapper;
    private final ApplicationMapper applicationMapper;
    private final ProjectMapper projectMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final FileObjectService fileService;
    private final AuditService auditService;
    private final ClubReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;

    public InvoiceService(InvoiceMapper invoiceMapper,
                          InvoiceFileRevisionMapper revisionMapper,
                          AttachmentMapper attachmentMapper,
                          ApplicationMapper applicationMapper,
                          ProjectMapper projectMapper,
                          DictionaryItemMapper dictionaryItemMapper,
                          FileObjectService fileService,
                          AuditService auditService,
                          ClubReviewMapper reviewMapper,
                          ObjectMapper objectMapper) {
        this.invoiceMapper = invoiceMapper;
        this.revisionMapper = revisionMapper;
        this.attachmentMapper = attachmentMapper;
        this.applicationMapper = applicationMapper;
        this.projectMapper = projectMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.fileService = fileService;
        this.auditService = auditService;
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
    }

    public List<InvoiceVO> listForApplication(String applicationId) {
        Application application = requireOwnApplication(applicationId);
        return invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                        .eq(Invoice::getOrganizationId, application.getOrganizationId())
                        .eq(Invoice::getApplicationId, applicationId)
                        .orderByAsc(Invoice::getCreatedAt)).stream()
                .map(this::toVO).toList();
    }

    public InvoiceVO detail(String invoiceId) {
        return toVO(requireOwnInvoice(invoiceId));
    }

    public InvoiceVO toAuthorizedReviewVO(Invoice invoice) {
        return toVO(invoice);
    }

    @Transactional
    public InvoiceVO create(String applicationId, String actorCasId,
                            CreateInvoiceRequest request) {
        Application application = requireOwnApplication(applicationId);
        requireApplicationEditable(application);
        fileService.requireReadyOwned(request.fileId(), actorCasId, ORIGINAL_PURPOSES);
        fileService.requireUnused(request.fileId());
        validateAmount(request.faceAmount(), request.claimedAmount());
        validateCategory(request.expenseCategoryItemId());
        Invoice invoice = Invoice.builder()
                .id(UlidGenerator.next()).organizationId(application.getOrganizationId())
                .applicationId(applicationId).invoiceType(request.invoiceType().trim())
                .invoiceCode(clean(request.invoiceCode())).invoiceNumber(clean(request.invoiceNumber()))
                .digitalInvoiceNo(clean(request.digitalInvoiceNo())).invoiceDate(request.invoiceDate())
                .buyerName(clean(request.buyerName())).buyerTaxNo(clean(request.buyerTaxNo()))
                .sellerName(clean(request.sellerName())).sellerTaxNo(clean(request.sellerTaxNo()))
                .faceAmount(request.faceAmount()).claimedAmount(request.claimedAmount())
                .currentFileId(request.fileId()).expenseCategoryItemId(clean(request.expenseCategoryItemId()))
                .fieldSourcesJson("{\"source\":\"MANUAL\"}")
                .status("DRAFT").version(0L).build();
        invoiceMapper.insert(invoice);
        appendFileRevision(invoice, request.fileId(), null, actorCasId);
        fileService.markReferenced(request.fileId());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_DRAFT_CREATED",
                "INVOICE", invoice.getId(), "{\"applicationId\":\"" + applicationId + "\"}");
        return toVO(invoice);
    }

    @Transactional
    public InvoiceVO update(String invoiceId, String actorCasId, UpdateInvoiceRequest request) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        requireInvoiceEditable(invoice);
        requireApplicationEditable(requireOwnApplication(invoice.getApplicationId()));
        requireVersion(invoice, request.version());
        Set<String> clear = request.clearFields() == null ? Set.of() : request.clearFields();
        Set<String> requestedFields = new HashSet<>(clear);
        if (request.invoiceType() != null) requestedFields.add("invoiceType");
        if (request.invoiceCode() != null) requestedFields.add("invoiceCode");
        if (request.invoiceNumber() != null) requestedFields.add("invoiceNumber");
        if (request.digitalInvoiceNo() != null) requestedFields.add("digitalInvoiceNo");
        if (request.invoiceDate() != null) requestedFields.add("invoiceDate");
        if (request.buyerName() != null) requestedFields.add("buyerName");
        if (request.buyerTaxNo() != null) requestedFields.add("buyerTaxNo");
        if (request.sellerName() != null) requestedFields.add("sellerName");
        if (request.sellerTaxNo() != null) requestedFields.add("sellerTaxNo");
        if (request.faceAmount() != null) requestedFields.add("faceAmount");
        if (request.claimedAmount() != null) requestedFields.add("claimedAmount");
        if (request.expenseCategoryItemId() != null) requestedFields.add("expenseCategoryItemId");
        requireReturnedFields(invoice, requestedFields);
        if (request.invoiceType() != null) invoice.setInvoiceType(required(request.invoiceType()));
        if (request.invoiceCode() != null) invoice.setInvoiceCode(clean(request.invoiceCode()));
        if (request.invoiceNumber() != null) invoice.setInvoiceNumber(clean(request.invoiceNumber()));
        if (request.digitalInvoiceNo() != null) invoice.setDigitalInvoiceNo(clean(request.digitalInvoiceNo()));
        if (request.invoiceDate() != null) invoice.setInvoiceDate(request.invoiceDate());
        if (request.buyerName() != null) invoice.setBuyerName(clean(request.buyerName()));
        if (request.buyerTaxNo() != null) invoice.setBuyerTaxNo(clean(request.buyerTaxNo()));
        if (request.sellerName() != null) invoice.setSellerName(clean(request.sellerName()));
        if (request.sellerTaxNo() != null) invoice.setSellerTaxNo(clean(request.sellerTaxNo()));
        if (request.faceAmount() != null) invoice.setFaceAmount(request.faceAmount());
        if (request.claimedAmount() != null) invoice.setClaimedAmount(request.claimedAmount());
        if (request.expenseCategoryItemId() != null) {
            validateCategory(request.expenseCategoryItemId());
            invoice.setExpenseCategoryItemId(clean(request.expenseCategoryItemId()));
        }
        applyClears(invoice, clear);
        validateAmount(invoice.getFaceAmount(), invoice.getClaimedAmount());
        updateWithVersion(invoice, request.version());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_DRAFT_UPDATED",
                "INVOICE", invoiceId, "{\"version\":" + invoice.getVersion() + "}");
        return toVO(invoice);
    }

    @Transactional
    public InvoiceVO replaceFile(String invoiceId, String actorCasId, ReplaceFileRequest request) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        requireInvoiceEditable(invoice);
        requireApplicationEditable(requireOwnApplication(invoice.getApplicationId()));
        requireVersion(invoice, request.version());
        requireReturnedFields(invoice, Set.of("currentFile"));
        fileService.requireReadyOwned(request.fileId(), actorCasId, ORIGINAL_PURPOSES);
        fileService.requireUnused(request.fileId());
        invoice.setCurrentFileId(request.fileId());
        updateWithVersion(invoice, request.version());
        appendFileRevision(invoice, request.fileId(), request.reason().trim(), actorCasId);
        fileService.markReferenced(request.fileId());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_FILE_REPLACED",
                "INVOICE", invoiceId, "{\"fileId\":\"" + request.fileId() + "\"}");
        return toVO(invoice);
    }

    @Transactional
    public AttachmentVO addAttachment(String invoiceId, String actorCasId,
                                      AddAttachmentRequest request) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        requireInvoiceEditable(invoice);
        requireApplicationEditable(requireOwnApplication(invoice.getApplicationId()));
        requireVersion(invoice, request.version());
        requireReturnedFields(invoice, Set.of("attachments"));
        fileService.requireReadyOwned(request.fileId(), actorCasId,
                "OTHER".equals(request.attachmentType()) ? Set.of("OTHER")
                        : Set.of(request.attachmentType(), "OTHER"));
        fileService.requireUnused(request.fileId());
        Attachment attachment = Attachment.builder()
                .id(UlidGenerator.next()).organizationId(invoice.getOrganizationId())
                .invoiceId(invoiceId).attachmentType(request.attachmentType())
                .fileId(request.fileId()).description(clean(request.description()))
                .status("ACTIVE").createdByCasId(actorCasId).build();
        attachmentMapper.insert(attachment);
        touchInvoice(invoice, request.version());
        fileService.markReferenced(request.fileId());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_ATTACHMENT_ADDED",
                "ATTACHMENT", attachment.getId(), "{\"invoiceId\":\"" + invoiceId + "\"}");
        return toAttachmentVO(attachment);
    }

    @Transactional
    public AttachmentVO voidAttachment(String invoiceId, String attachmentId, String actorCasId,
                                       VersionedReasonRequest request) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        requireInvoiceEditable(invoice);
        requireApplicationEditable(requireOwnApplication(invoice.getApplicationId()));
        requireVersion(invoice, request.version());
        requireReturnedFields(invoice, Set.of("attachments"));
        Attachment attachment = attachmentMapper.selectOne(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOrganizationId, invoice.getOrganizationId())
                .eq(Attachment::getInvoiceId, invoiceId).eq(Attachment::getId, attachmentId));
        if (attachment == null) throw new BusinessException(BizCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if ("VOIDED".equals(attachment.getStatus())) return toAttachmentVO(attachment);
        attachment.setStatus("VOIDED");
        attachment.setVoidReason(request.reason().trim());
        attachment.setVoidedByCasId(actorCasId);
        attachment.setVoidedAt(Instant.now());
        attachmentMapper.updateById(attachment);
        touchInvoice(invoice, request.version());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_ATTACHMENT_VOIDED",
                "ATTACHMENT", attachmentId, "{\"invoiceId\":\"" + invoiceId + "\"}");
        return toAttachmentVO(attachment);
    }

    @Transactional
    public void deleteDraft(String invoiceId, String actorCasId, long version) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        requireApplicationEditable(requireOwnApplication(invoice.getApplicationId()));
        requireVersion(invoice, version);
        attachmentMapper.deleteForInvoice(invoice.getOrganizationId(), invoiceId);
        revisionMapper.deleteForInvoice(invoice.getOrganizationId(), invoiceId);
        if (invoiceMapper.deleteDraft(invoice.getOrganizationId(), invoiceId, version) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_DRAFT_DELETED",
                "INVOICE", invoiceId, "{}");
    }

    @Transactional
    public InvoiceVO voidFormal(String invoiceId, String actorCasId, VersionedReasonRequest request) {
        Invoice invoice = requireOwnInvoice(invoiceId);
        requireVersion(invoice, request.version());
        if (Set.of("DRAFT", "VOIDED", "IN_EXPORT_BATCH", "ARCHIVED").contains(invoice.getStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        invoice.setStatus("VOIDED");
        invoice.setVoidReason(request.reason().trim());
        invoice.setVoidedByCasId(actorCasId);
        invoice.setVoidedAt(Instant.now());
        updateWithVersion(invoice, request.version());
        applicationMapper.refreshDerivedStatus(invoice.getOrganizationId(), invoice.getApplicationId());
        auditService.append(invoice.getOrganizationId(), actorCasId, "INVOICE_VOIDED",
                "INVOICE", invoiceId, "{\"reason\":\"provided\"}");
        return toVO(invoice);
    }

    private Application requireOwnApplication(String applicationId) {
        Application application = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(Application::getId, applicationId)
                .eq(Application::getApplicantCasId, TenantContext.requireCasId()));
        if (application == null) throw new BusinessException(BizCode.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        return application;
    }

    private Invoice requireOwnInvoice(String invoiceId) {
        Invoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(Invoice::getId, invoiceId));
        if (invoice == null) throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        requireOwnApplication(invoice.getApplicationId());
        return invoice;
    }

    private void requireApplicationEditable(Application application) {
        if (!EDITABLE.contains(application.getStatus())) {
            throw new BusinessException(BizCode.APPLICATION_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        Project project = projectMapper.lockForApplication(
                application.getOrganizationId(), application.getId());
        if (project == null || "ARCHIVED".equals(project.getStatus())) {
            throw new BusinessException(BizCode.PROJECT_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
    }

    private void requireInvoiceEditable(Invoice invoice) {
        if (!EDITABLE.contains(invoice.getStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
    }

    private void requireVersion(Invoice invoice, long version) {
        if (invoice.getVersion() == null || invoice.getVersion() != version) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private void validateAmount(BigDecimal face, BigDecimal claimed) {
        if (face == null || claimed == null || face.signum() < 0 || claimed.signum() <= 0
                || claimed.compareTo(face) > 0 || face.scale() > 2 || claimed.scale() > 2
                || face.precision() - face.scale() > 10 || claimed.precision() - claimed.scale() > 10) {
            throw new BusinessException(BizCode.INVOICE_AMOUNT_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateCategory(String itemId) {
        String value = clean(itemId);
        if (value == null) return;
        DictionaryItem item = dictionaryItemMapper.findEnabledByType(
                TenantContext.requireOrganizationId(), value, "EXPENSE_CATEGORY");
        if (item == null) throw new BusinessException(BizCode.DICTIONARY_ITEM_NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    private void appendFileRevision(Invoice invoice, String fileId, String reason, String actorCasId) {
        revisionMapper.insert(InvoiceFileRevision.builder().id(UlidGenerator.next())
                .organizationId(invoice.getOrganizationId()).invoiceId(invoice.getId())
                .fileId(fileId).revisionNo(revisionMapper.maxRevision(
                        invoice.getOrganizationId(), invoice.getId()) + 1)
                .replacementReason(reason).replacedByCasId(actorCasId).build());
    }

    private void touchInvoice(Invoice invoice, long version) {
        invoice.setVersion(version);
        if (invoiceMapper.updateById(invoice) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        invoice.setVersion(version + 1);
    }

    private void updateWithVersion(Invoice invoice, long version) {
        touchInvoice(invoice, version);
    }

    private void applyClears(Invoice invoice, Set<String> clear) {
        Set<String> fields = new HashSet<>(clear);
        if (fields.contains("invoiceCode")) invoice.setInvoiceCode(null);
        if (fields.contains("invoiceNumber")) invoice.setInvoiceNumber(null);
        if (fields.contains("digitalInvoiceNo")) invoice.setDigitalInvoiceNo(null);
        if (fields.contains("invoiceDate")) invoice.setInvoiceDate(null);
        if (fields.contains("buyerName")) invoice.setBuyerName(null);
        if (fields.contains("buyerTaxNo")) invoice.setBuyerTaxNo(null);
        if (fields.contains("sellerName")) invoice.setSellerName(null);
        if (fields.contains("sellerTaxNo")) invoice.setSellerTaxNo(null);
        if (fields.contains("expenseCategoryItemId")) invoice.setExpenseCategoryItemId(null);
    }

    private void requireReturnedFields(Invoice invoice, Set<String> requestedFields) {
        if (!"RETURNED".equals(invoice.getStatus()) || requestedFields.isEmpty()) return;
        String json = reviewMapper.findLatestReturnFields(invoice.getOrganizationId(), invoice.getId());
        try {
            Set<String> allowed = json == null ? Set.of()
                    : objectMapper.readValue(json, new TypeReference<Set<String>>() {});
            if (!allowed.containsAll(requestedFields)) {
                throw new BusinessException(BizCode.REVIEW_FIELD_NOT_ALLOWED, HttpStatus.FORBIDDEN);
            }
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String required(String value) {
        String result = clean(value);
        if (result == null) throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        return result;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InvoiceVO toVO(Invoice invoice) {
        List<InvoiceFileRevisionVO> revisions = revisionMapper
                .findForInvoice(invoice.getOrganizationId(), invoice.getId()).stream()
                .map(r -> new InvoiceFileRevisionVO(r.getId(), r.getFileId(), r.getRevisionNo(),
                        r.getReplacementReason(), r.getCreatedAt())).toList();
        List<AttachmentVO> attachments = attachmentMapper
                .findForInvoice(invoice.getOrganizationId(), invoice.getId()).stream()
                .map(this::toAttachmentVO).toList();
        return new InvoiceVO(invoice.getId(), invoice.getApplicationId(), invoice.getInvoiceType(),
                invoice.getInvoiceCode(), invoice.getInvoiceNumber(), invoice.getDigitalInvoiceNo(),
                invoice.getInvoiceDate(), invoice.getBuyerName(), invoice.getBuyerTaxNo(),
                invoice.getSellerName(), invoice.getSellerTaxNo(), invoice.getFaceAmount(),
                invoice.getClaimedAmount(), invoice.getCurrentFileId(), invoice.getExpenseCategoryItemId(),
                invoice.getStatus(), invoice.getVoidReason(), invoice.getVoidedAt(),
                invoice.getVersion() == null ? 0 : invoice.getVersion(), invoice.getCreatedAt(),
                invoice.getUpdatedAt(), revisions, attachments);
    }

    private AttachmentVO toAttachmentVO(Attachment attachment) {
        return new AttachmentVO(attachment.getId(), attachment.getAttachmentType(),
                attachment.getFileId(), attachment.getDescription(), attachment.getStatus(),
                attachment.getVoidReason(), attachment.getVoidedAt(), attachment.getCreatedAt());
    }
}
