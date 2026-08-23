package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.ReviewDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.*;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReviewService {
    private static final Set<String> QUEUE_STATUSES = Set.of(
            "SUBMITTED", "IN_REVIEW", "RETURNED", "INTERNALLY_APPROVED", "REJECTED", "VOIDED");

    private final ClubReviewMapper reviewMapper;
    private final InvoiceMapper invoiceMapper;
    private final ApplicationMapper applicationMapper;
    private final InvoiceTagMapper tagMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final InvoicePrecheckResultMapper precheckMapper;
    private final AuthorizationService authorizationService;
    private final InvoiceService invoiceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final InvoicePrecheckService precheckService;
    private final PaperService paperService;
    private final FileObjectMapper fileObjectMapper;

    public ReviewService(ClubReviewMapper reviewMapper,
                         InvoiceMapper invoiceMapper,
                         ApplicationMapper applicationMapper,
                         InvoiceTagMapper tagMapper,
                         DictionaryItemMapper dictionaryItemMapper,
                         InvoicePrecheckResultMapper precheckMapper,
                         AuthorizationService authorizationService,
                         InvoiceService invoiceService,
                         AuditService auditService,
                         ObjectMapper objectMapper,
                         InvoicePrecheckService precheckService,
                         PaperService paperService,
                         FileObjectMapper fileObjectMapper) {
        this.reviewMapper = reviewMapper;
        this.invoiceMapper = invoiceMapper;
        this.applicationMapper = applicationMapper;
        this.tagMapper = tagMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.precheckMapper = precheckMapper;
        this.authorizationService = authorizationService;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.precheckService = precheckService;
        this.paperService = paperService;
        this.fileObjectMapper = fileObjectMapper;
    }

    public PageResult<ReviewQueueItemVO> queue(long page, long pageSize, String projectId,
                                                String status, String applicantCasId) {
        if (status != null && !QUEUE_STATUSES.contains(status)) invalid();
        if (projectId != null) authorizationService.requireReviewProject(projectId);
        boolean unrestricted = authorizationService.hasPermission("application:review");
        IPage<ReviewQueueItemVO> result = reviewMapper.findQueue(new Page<>(page, pageSize),
                TenantContext.requireOrganizationId(), TenantContext.requireCasId(), unrestricted,
                clean(projectId), clean(status), clean(applicantCasId));
        return new PageResult<>(result.getRecords(), page, pageSize, result.getTotal());
    }

    public ReviewInvoiceDetailVO detail(String invoiceId) {
        ReviewQueueItemVO summary = requireReviewSummary(invoiceId);
        Invoice invoice = requireInvoice(invoiceId);
        FileObject currentFile = fileObjectMapper.findByOrganizationAndId(
                invoice.getOrganizationId(), invoice.getCurrentFileId());
        ReviewInvoiceDetailVO.CurrentFileVO currentFileVO = currentFile == null ? null
                : new ReviewInvoiceDetailVO.CurrentFileVO(currentFile.getId(),
                currentFile.getOriginalName(), currentFile.getContentType());
        return new ReviewInvoiceDetailVO(summary, invoiceService.toAuthorizedReviewVO(invoice),
                currentFileVO, invoice.getInternalNote(), new LinkedHashSet<>(tagMapper.findTagIds(
                invoice.getOrganizationId(), invoiceId)), historyRecords(invoiceId));
    }

    public List<ClubReviewVO> history(String invoiceId) {
        ReviewQueueItemVO summary = requireSummary(invoiceId);
        boolean owner = summary.applicantCasId().equals(TenantContext.requireCasId());
        if (!owner && !authorizationService.canReviewProject(summary.projectId())) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        return historyRecords(invoiceId);
    }

    public ReviewStatsVO stats(String projectId) {
        String cleanedProjectId = clean(projectId);
        if (cleanedProjectId != null) authorizationService.requireReviewProject(cleanedProjectId);
        boolean unrestricted = authorizationService.hasPermission("application:review");
        return reviewMapper.findStats(TenantContext.requireOrganizationId(),
                TenantContext.requireCasId(), unrestricted, cleanedProjectId);
    }

    @Transactional
    public ReviewInvoiceDetailVO start(String invoiceId, String actorCasId,
                                       StartReviewRequest request) {
        ReviewQueueItemVO summary = requireReviewSummary(invoiceId);
        Invoice invoice = requireInvoice(invoiceId);
        requireVersion(invoice, request.version());
        if ("IN_REVIEW".equals(invoice.getStatus())) return detail(invoiceId);
        if (!"SUBMITTED".equals(invoice.getStatus())) state();
        precheckService.run(invoiceId, actorCasId);
        invoice.setStatus("IN_REVIEW");
        updateInvoice(invoice, request.version());
        append(invoice, actorCasId, "START_REVIEW", null, null, null, null);
        refreshApplication(invoice);
        audit(invoice, actorCasId, "INVOICE_REVIEW_STARTED");
        return detail(summary.invoiceId());
    }

    @Transactional
    public ReviewInvoiceDetailVO approve(String invoiceId, String actorCasId,
                                         ApproveReviewRequest request) {
        requireReviewSummary(invoiceId);
        Invoice invoice = requireInvoice(invoiceId);
        requireVersion(invoice, request.version());
        if (!"IN_REVIEW".equals(invoice.getStatus())) {
            throw new BusinessException(BizCode.REVIEW_NOT_STARTED, HttpStatus.CONFLICT);
        }
        requireNoBlockingPrecheck(invoice);
        applyOrganizationFields(invoice, request.expenseCategoryItemId(), request.internalNote(),
                request.tagItemIds(), actorCasId);
        invoice.setStatus("INTERNALLY_APPROVED");
        updateInvoice(invoice, request.version());
        append(invoice, actorCasId, "APPROVE", null, null, null, null);
        refreshApplication(invoice);
        paperService.ensureForApproved(invoiceId, actorCasId);
        audit(invoice, actorCasId, "INVOICE_REVIEW_APPROVED");
        return detail(invoiceId);
    }

    @Transactional
    public ReviewInvoiceDetailVO returnForCorrection(String invoiceId, String actorCasId,
                                                      ReturnReviewRequest request) {
        requireReviewSummary(invoiceId);
        Invoice invoice = requireInvoice(invoiceId);
        requireVersion(invoice, request.version());
        requireInReview(invoice);
        requireDictionaryItem(request.reasonItemId(), "RETURN_REASON");
        if (request.returnFields() == null || request.returnFields().isEmpty()) {
            throw new BusinessException(BizCode.REVIEW_RETURN_FIELD_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        String returnFieldsJson = writeJson(new LinkedHashSet<>(request.returnFields()));
        invoice.setStatus("RETURNED");
        updateInvoice(invoice, request.version());
        append(invoice, actorCasId, "RETURN", request.reasonItemId(), returnFieldsJson,
                clean(request.comment()), null);
        refreshApplication(invoice);
        audit(invoice, actorCasId, "INVOICE_REVIEW_RETURNED");
        return detail(invoiceId);
    }

    @Transactional
    public ReviewInvoiceDetailVO reject(String invoiceId, String actorCasId,
                                        RejectReviewRequest request) {
        requireReviewSummary(invoiceId);
        Invoice invoice = requireInvoice(invoiceId);
        requireVersion(invoice, request.version());
        requireInReview(invoice);
        requireDictionaryItem(request.reasonItemId(), "RETURN_REASON");
        invoice.setStatus("REJECTED");
        updateInvoice(invoice, request.version());
        append(invoice, actorCasId, "REJECT", request.reasonItemId(), null,
                clean(request.comment()), null);
        refreshApplication(invoice);
        audit(invoice, actorCasId, "INVOICE_REVIEW_REJECTED");
        return detail(invoiceId);
    }

    @Transactional
    public List<ReviewInvoiceDetailVO> batchApprove(String actorCasId,
                                                    BatchApproveRequest request) {
        if (request.invoices() == null || request.invoices().isEmpty()) invalid();
        if (request.invoices().size() > 100) {
            throw new BusinessException(BizCode.BATCH_SIZE_EXCEEDED, HttpStatus.BAD_REQUEST);
        }
        Set<String> duplicateGuard = new HashSet<>();
        String batchId = UlidGenerator.next();
        for (BatchApproveItem item : request.invoices()) {
            if (!duplicateGuard.add(item.invoiceId())) invalid();
            requireReviewSummary(item.invoiceId());
            Invoice invoice = requireInvoice(item.invoiceId());
            requireVersion(invoice, item.version());
            boolean implicitStart = "SUBMITTED".equals(invoice.getStatus());
            if (!implicitStart && !"IN_REVIEW".equals(invoice.getStatus())) state();
            if (implicitStart) precheckService.run(invoice.getId(), actorCasId);
            requireNoBlockingPrecheck(invoice);
            if (implicitStart) {
                append(invoice, actorCasId, "START_REVIEW", null, null, null, batchId);
            }
            invoice.setStatus("INTERNALLY_APPROVED");
            updateInvoice(invoice, item.version());
            append(invoice, actorCasId, "APPROVE", null, null, null, batchId);
            refreshApplication(invoice);
            paperService.ensureForApproved(invoice.getId(), actorCasId);
            audit(invoice, actorCasId, "INVOICE_REVIEW_APPROVED");
        }
        return request.invoices().stream().map(item -> detail(item.invoiceId())).toList();
    }

    private ReviewQueueItemVO requireReviewSummary(String invoiceId) {
        ReviewQueueItemVO summary = requireSummary(invoiceId);
        authorizationService.requireReviewProject(summary.projectId());
        return summary;
    }

    private ReviewQueueItemVO requireSummary(String invoiceId) {
        ReviewQueueItemVO summary = reviewMapper.findSummary(
                TenantContext.requireOrganizationId(), invoiceId);
        if (summary == null) throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        return summary;
    }

    private Invoice requireInvoice(String invoiceId) {
        Invoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(Invoice::getId, invoiceId));
        if (invoice == null) throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        return invoice;
    }

    private void requireVersion(Invoice invoice, long version) {
        if (invoice.getVersion() == null || invoice.getVersion() != version) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private void requireInReview(Invoice invoice) {
        if (!"IN_REVIEW".equals(invoice.getStatus())) {
            throw new BusinessException(BizCode.REVIEW_NOT_STARTED, HttpStatus.CONFLICT);
        }
    }

    private void requireNoBlockingPrecheck(Invoice invoice) {
        if (precheckMapper.countUnresolvedBlocks(invoice.getOrganizationId(), invoice.getId()) > 0) {
            throw new BusinessException(BizCode.RULE_BLOCKED, HttpStatus.CONFLICT);
        }
    }

    private void applyOrganizationFields(Invoice invoice, String categoryId, String note,
                                         Set<String> tagItemIds, String actorCasId) {
        if (categoryId != null) {
            requireDictionaryItem(categoryId, "EXPENSE_CATEGORY");
            invoice.setExpenseCategoryItemId(clean(categoryId));
        }
        if (note != null) invoice.setInternalNote(clean(note));
        if (tagItemIds != null) {
            tagMapper.deleteForInvoice(invoice.getOrganizationId(), invoice.getId());
            for (String tagId : new LinkedHashSet<>(tagItemIds)) {
                requireDictionaryItem(tagId, "TAG");
                tagMapper.insert(InvoiceTag.builder().invoiceId(invoice.getId())
                        .organizationId(invoice.getOrganizationId()).tagItemId(tagId)
                        .addedByCasId(actorCasId).build());
            }
        }
    }

    private void requireDictionaryItem(String itemId, String type) {
        if (dictionaryItemMapper.findEnabledByType(TenantContext.requireOrganizationId(),
                clean(itemId), type) == null) {
            throw new BusinessException(BizCode.DICTIONARY_ITEM_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    private void updateInvoice(Invoice invoice, long version) {
        invoice.setVersion(version);
        if (invoiceMapper.updateById(invoice) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        invoice.setVersion(version + 1);
    }

    private void refreshApplication(Invoice invoice) {
        applicationMapper.refreshDerivedStatus(invoice.getOrganizationId(), invoice.getApplicationId());
    }

    private void append(Invoice invoice, String actorCasId, String action, String reasonItemId,
                        String returnFieldsJson, String comment, String batchId) {
        reviewMapper.insert(ClubReview.builder().id(UlidGenerator.next())
                .organizationId(invoice.getOrganizationId()).invoiceId(invoice.getId())
                .reviewerCasId(actorCasId).action(action).reasonItemId(reasonItemId)
                .returnFieldsJson(returnFieldsJson).comment(comment)
                .batchOperationId(batchId).build());
    }

    private List<ClubReviewVO> historyRecords(String invoiceId) {
        return reviewMapper.findForInvoice(TenantContext.requireOrganizationId(), invoiceId).stream()
                .map(review -> new ClubReviewVO(review.id(), review.invoiceId(),
                        review.reviewerCasId(), review.reviewerName(), review.action(), review.reasonItemId(),
                        readFields(review.returnFieldsJson()), review.comment(),
                        review.batchOperationId(), review.createdAt())).toList();
    }

    private Set<String> readFields(String json) {
        if (json == null) return Set.of();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashSet<String>>() {});
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void audit(Invoice invoice, String actorCasId, String action) {
        auditService.append(invoice.getOrganizationId(), actorCasId, action,
                "INVOICE", invoice.getId(), "{\"status\":\"" + invoice.getStatus() + "\"}");
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }

    private void state() {
        throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
    }
}
