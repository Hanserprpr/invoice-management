package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.PrecheckDtos.ResolvePrecheckRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.data.po.InvoicePrecheckResult;
import cn.sduonline.invoice.data.vo.PrecheckResultVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.InvoiceMapper;
import cn.sduonline.invoice.mapper.InvoicePrecheckResultMapper;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.mapper.InvoicePrecheckResultMapper.DuplicateMatch;
import cn.sduonline.invoice.mapper.InvoicePrecheckResultMapper.InvoiceProjectContext;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InvoicePrecheckService {
    private final InvoiceMapper invoiceMapper;
    private final InvoicePrecheckResultMapper resultMapper;
    private final LedgerMapper ledgerMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final RuleExecutionService ruleExecutionService;

    public InvoicePrecheckService(InvoiceMapper invoiceMapper,
                                  InvoicePrecheckResultMapper resultMapper,
                                  LedgerMapper ledgerMapper,
                                  AuthorizationService authorizationService,
                                  AuditService auditService,
                                  RuleExecutionService ruleExecutionService) {
        this.invoiceMapper = invoiceMapper;
        this.resultMapper = resultMapper;
        this.ledgerMapper = ledgerMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.ruleExecutionService = ruleExecutionService;
    }

    @Transactional
    public List<PrecheckResultVO> run(String invoiceId, String actorCasId) {
        Invoice invoice = requireInvoice(invoiceId);
        requireAccess(invoiceId);
        String organizationId = invoice.getOrganizationId();
        InvoiceProjectContext context = resultMapper.findContext(organizationId, invoiceId);
        if (context == null) throw notFound();
        DuplicateMatch exact = exactMatch(invoice);
        append(invoice, context.ruleSetVersionId(), "EXACT_DUPLICATE", "DUPLICATE_INVOICE",
                "BLOCK", exact == null ? "PASS" : "HIT",
                exact == null ? "未发现精确重复" : "平台内存在相同票据标识或相同原文件",
                exact);

        DuplicateMatch suspected = suspectedMatch(invoice, exact);
        append(invoice, context.ruleSetVersionId(), "SUSPECTED_DUPLICATE", "SAME_SELLER_DATE_AMOUNT",
                "WARNING", suspected == null ? "PASS" : "HIT",
                suspected == null ? "未发现疑似重复" : "平台内存在销售方、日期和金额均相同的票据",
                suspected);

        ruleExecutionService.execute(invoice, context.ruleSetVersionId());
        auditService.append(organizationId, actorCasId, "INVOICE_PRECHECK_RUN", "INVOICE",
                invoiceId, "{\"exactDuplicate\":" + (exact != null)
                        + ",\"suspectedDuplicate\":" + (suspected != null) + "}");
        return list(invoiceId);
    }

    public List<PrecheckResultVO> list(String invoiceId) {
        requireInvoice(invoiceId);
        requireAccess(invoiceId);
        return resultMapper.findForInvoice(TenantContext.requireOrganizationId(), invoiceId)
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public PrecheckResultVO resolve(String invoiceId, String resultId, String actorCasId,
                                    ResolvePrecheckRequest request) {
        InvoiceProjectContext context = resultMapper.findContext(
                TenantContext.requireOrganizationId(), invoiceId);
        if (context == null) throw notFound();
        authorizationService.requireReviewProject(context.projectId());
        InvoicePrecheckResult existing = resultMapper.selectOne(
                new LambdaQueryWrapper<InvoicePrecheckResult>()
                        .eq(InvoicePrecheckResult::getOrganizationId, TenantContext.requireOrganizationId())
                        .eq(InvoicePrecheckResult::getInvoiceId, invoiceId)
                        .eq(InvoicePrecheckResult::getId, resultId));
        if (existing == null) throw new BusinessException(BizCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if ("EXACT_DUPLICATE".equals(existing.getCheckType())
                && "ACCEPTED_RISK".equals(request.resolution())) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (resultMapper.resolve(TenantContext.requireOrganizationId(), invoiceId, resultId,
                request.resolution(), actorCasId, request.comment().trim(), Instant.now()) != 1) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        InvoicePrecheckResult result = resultMapper.selectOne(
                new LambdaQueryWrapper<InvoicePrecheckResult>()
                        .eq(InvoicePrecheckResult::getOrganizationId, TenantContext.requireOrganizationId())
                        .eq(InvoicePrecheckResult::getInvoiceId, invoiceId)
                        .eq(InvoicePrecheckResult::getId, resultId));
        auditService.append(TenantContext.requireOrganizationId(), actorCasId,
                "INVOICE_PRECHECK_RESOLVED", "INVOICE", invoiceId,
                "{\"resultId\":\"" + resultId + "\",\"resolution\":\""
                        + request.resolution() + "\"}");
        return toVO(result);
    }

    private DuplicateMatch exactMatch(Invoice invoice) {
        DuplicateMatch match = null;
        if (hasText(invoice.getDigitalInvoiceNo())) {
            match = resultMapper.findExactDigital(invoice.getId(), invoice.getOrganizationId(),
                    invoice.getDigitalInvoiceNo().trim());
        }
        if (match == null && hasText(invoice.getInvoiceCode()) && hasText(invoice.getInvoiceNumber())) {
            match = resultMapper.findExactCodeNumber(invoice.getId(), invoice.getOrganizationId(),
                    invoice.getInvoiceCode().trim(), invoice.getInvoiceNumber().trim());
        }
        if (match == null && hasText(invoice.getCurrentFileId())) {
            match = resultMapper.findExactFile(invoice.getId(), invoice.getOrganizationId(),
                    invoice.getCurrentFileId());
        }
        return match;
    }

    private DuplicateMatch suspectedMatch(Invoice invoice, DuplicateMatch exact) {
        if (exact != null || !hasText(invoice.getSellerTaxNo()) || invoice.getInvoiceDate() == null
                || invoice.getFaceAmount() == null) return null;
        return resultMapper.findSuspected(invoice.getId(), invoice.getOrganizationId(),
                invoice.getSellerTaxNo().trim(), invoice.getInvoiceDate(), invoice.getFaceAmount());
    }

    private void append(Invoice invoice, String ruleVersionId, String type, String code,
                        String severity, String result, String reason, DuplicateMatch match) {
        boolean sameOrganization = match != null
                && invoice.getOrganizationId().equals(match.organizationId());
        resultMapper.insert(InvoicePrecheckResult.builder().id(UlidGenerator.next())
                .organizationId(invoice.getOrganizationId()).invoiceId(invoice.getId())
                .ruleSetVersionId(ruleVersionId).checkType(type).ruleCode(code)
                .severity(severity).result(result).reason(reason)
                .matchedInvoiceId(match == null ? null : match.invoiceId())
                .evidenceJson(match == null ? null
                        : "{\"scope\":\"" + (sameOrganization ? "CURRENT_ORGANIZATION" : "PLATFORM") + "\"}")
                .build());
    }

    private Invoice requireInvoice(String invoiceId) {
        Invoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(Invoice::getId, invoiceId));
        if (invoice == null) throw notFound();
        return invoice;
    }

    private void requireAccess(String invoiceId) {
        boolean unrestricted = authorizationService.hasPermission("application:review")
                || authorizationService.hasPermission("audit-log:read");
        if (!ledgerMapper.canAccessInvoice(TenantContext.requireOrganizationId(),
                TenantContext.requireCasId(), unrestricted, invoiceId)) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private PrecheckResultVO toVO(InvoicePrecheckResult row) {
        return new PrecheckResultVO(row.getId(), row.getInvoiceId(), row.getRuleSetVersionId(),
                row.getCheckType(), row.getRuleCode(), row.getSeverity(), row.getResult(),
                row.getReason(), row.getResolution(), row.getResolvedByCasId(),
                row.getResolutionComment(), row.getResolvedAt(), row.getCreatedAt());
    }

    private BusinessException notFound() {
        return new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
