package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.RecognitionDtos.ConfirmSuggestionsRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.data.po.RecognitionSuggestion;
import cn.sduonline.invoice.data.vo.RecognitionJobVO;
import cn.sduonline.invoice.data.vo.RecognitionSuggestionVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.mapper.InvoiceMapper;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.mapper.RecognitionInvoiceMapper;
import cn.sduonline.invoice.mapper.RecognitionSuggestionMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecognitionService {
    private final InvoiceMapper invoiceMapper;
    private final RecognitionInvoiceMapper recognitionInvoiceMapper;
    private final AsyncJobMapper jobMapper;
    private final RecognitionSuggestionMapper suggestionMapper;
    private final LedgerMapper ledgerMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RecognitionService(InvoiceMapper invoiceMapper,
                              RecognitionInvoiceMapper recognitionInvoiceMapper,
                              AsyncJobMapper jobMapper,
                              RecognitionSuggestionMapper suggestionMapper,
                              LedgerMapper ledgerMapper,
                              AuthorizationService authorizationService,
                              AuditService auditService, ObjectMapper objectMapper) {
        this.invoiceMapper = invoiceMapper;
        this.recognitionInvoiceMapper = recognitionInvoiceMapper;
        this.jobMapper = jobMapper;
        this.suggestionMapper = suggestionMapper;
        this.ledgerMapper = ledgerMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecognitionJobVO start(String invoiceId, String actorCasId) {
        Invoice owned = requireOwned(invoiceId, actorCasId);
        Invoice invoice = recognitionInvoiceMapper.lockById(owned.getOrganizationId(), invoiceId);
        if (invoice == null) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        AsyncJob active = jobMapper.findActiveForTargetForUpdate(invoice.getOrganizationId(),
                RecognitionWorker.JOB_TYPE, "INVOICE", invoiceId);
        if (active != null) {
            prepareInvoiceForStart(invoice, true);
            return toJobVO(active);
        }
        if (!Set.of("DRAFT", "RETURNED").contains(invoice.getStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        AsyncJob job = AsyncJob.builder().id(UlidGenerator.next())
                .organizationId(invoice.getOrganizationId())
                .jobType(RecognitionWorker.JOB_TYPE).targetType("INVOICE").targetId(invoiceId)
                .status("PENDING").progress(0).attemptCount(0).maxAttempts(3)
                .createdByCasId(actorCasId).build();
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException exception) {
            AsyncJob winner = jobMapper.findActiveForTargetForUpdate(invoice.getOrganizationId(),
                    RecognitionWorker.JOB_TYPE, "INVOICE", invoiceId);
            if (winner == null) throw exception;
            prepareInvoiceForStart(invoice, true);
            return toJobVO(winner);
        }
        prepareInvoiceForStart(invoice, false);
        auditService.append(invoice.getOrganizationId(), actorCasId,
                "INVOICE_RECOGNITION_REQUESTED", "INVOICE", invoiceId,
                "{\"jobId\":\"" + job.getId() + "\"}");
        return toJobVO(job);
    }

    public List<RecognitionJobVO> jobs(String invoiceId) {
        Invoice invoice = requireAccessible(invoiceId);
        return jobMapper.selectList(new LambdaQueryWrapper<AsyncJob>()
                        .eq(AsyncJob::getOrganizationId, invoice.getOrganizationId())
                        .eq(AsyncJob::getJobType, RecognitionWorker.JOB_TYPE)
                        .eq(AsyncJob::getTargetType, "INVOICE")
                        .eq(AsyncJob::getTargetId, invoiceId)
                        .orderByDesc(AsyncJob::getCreatedAt)).stream()
                .map(this::toJobVO).toList();
    }

    public List<RecognitionSuggestionVO> suggestions(String invoiceId) {
        Invoice invoice = requireAccessible(invoiceId);
        return suggestionMapper.selectList(new LambdaQueryWrapper<RecognitionSuggestion>()
                        .eq(RecognitionSuggestion::getOrganizationId, invoice.getOrganizationId())
                        .eq(RecognitionSuggestion::getInvoiceId, invoiceId)
                        .orderByAsc(RecognitionSuggestion::getCreatedAt)
                        .orderByAsc(RecognitionSuggestion::getFieldPath)).stream()
                .map(this::toSuggestionVO).toList();
    }

    @Transactional
    public List<RecognitionSuggestionVO> confirm(String invoiceId, String actorCasId,
                                                  ConfirmSuggestionsRequest request) {
        Invoice owned = requireOwned(invoiceId, actorCasId);
        Invoice invoice = recognitionInvoiceMapper.lockById(owned.getOrganizationId(), invoiceId);
        if (invoice == null) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        AsyncJob active = jobMapper.findActiveForTargetForUpdate(invoice.getOrganizationId(),
                RecognitionWorker.JOB_TYPE, "INVOICE", invoiceId);
        if (!Set.of("DRAFT", "RETURNED", "PENDING_RECOGNITION").contains(invoice.getStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        Set<String> ids = new HashSet<>();
        Instant now = Instant.now();
        for (var decision : request.decisions()) {
            if (!ids.add(decision.suggestionId())) {
                throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
            }
            RecognitionSuggestion suggestion = suggestionMapper.selectOne(
                    new LambdaQueryWrapper<RecognitionSuggestion>()
                            .eq(RecognitionSuggestion::getOrganizationId, invoice.getOrganizationId())
                            .eq(RecognitionSuggestion::getInvoiceId, invoiceId)
                            .eq(RecognitionSuggestion::getId, decision.suggestionId()));
            if (suggestion == null) {
                throw new BusinessException(BizCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            String finalValue = normalizeFinalValue(decision.status(), decision.finalValue(),
                    suggestion.getSuggestedValue());
            if (suggestionMapper.confirm(invoice.getOrganizationId(), invoiceId,
                    decision.suggestionId(), decision.status(), finalValue, actorCasId, now) != 1) {
                throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
            }
        }
        long pending = suggestionMapper.selectCount(new LambdaQueryWrapper<RecognitionSuggestion>()
                .eq(RecognitionSuggestion::getOrganizationId, invoice.getOrganizationId())
                .eq(RecognitionSuggestion::getInvoiceId, invoiceId)
                .eq(RecognitionSuggestion::getStatus, "PENDING"));
        if (pending == 0 && "PENDING_RECOGNITION".equals(invoice.getStatus())
                && active == null) {
            if (recognitionInvoiceMapper.restoreDraftIfPending(
                    invoice.getOrganizationId(), invoiceId) != 1) {
                throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
            }
        }
        auditService.append(invoice.getOrganizationId(), actorCasId,
                "RECOGNITION_SUGGESTIONS_CONFIRMED", "INVOICE", invoiceId,
                "{\"count\":" + request.decisions().size() + "}");
        return suggestions(invoiceId);
    }

    private Invoice requireOwned(String invoiceId, String actorCasId) {
        String currentCasId = TenantContext.requireCasId();
        if (!currentCasId.equals(actorCasId)) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Invoice invoice = recognitionInvoiceMapper.findOwnedById(
                TenantContext.requireOrganizationId(), invoiceId, currentCasId);
        if (invoice == null) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return invoice;
    }

    private void prepareInvoiceForStart(Invoice invoice, boolean allowAlreadyPending) {
        if ("DRAFT".equals(invoice.getStatus())) {
            if (recognitionInvoiceMapper.markPendingIfDraft(
                    invoice.getOrganizationId(), invoice.getId()) != 1) {
                throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
            }
            return;
        }
        if ("RETURNED".equals(invoice.getStatus())
                || (allowAlreadyPending && "PENDING_RECOGNITION".equals(invoice.getStatus()))) {
            return;
        }
        throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
    }

    private Invoice requireAccessible(String invoiceId) {
        String organizationId = TenantContext.requireOrganizationId();
        String actorCasId = TenantContext.requireCasId();
        boolean unrestricted = authorizationService.hasPermission("application:review")
                || authorizationService.hasPermission("audit-log:read");
        if (!ledgerMapper.canAccessInvoice(organizationId, actorCasId, unrestricted, invoiceId)) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Invoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, organizationId).eq(Invoice::getId, invoiceId));
        if (invoice == null) throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        return invoice;
    }

    private String normalizeFinalValue(String status, String finalValue, String suggestedValue) {
        if ("REJECTED".equals(status)) return null;
        String normalized = finalValue == null ? null : finalValue.trim();
        if ("ACCEPTED".equals(status) && (normalized == null || normalized.isEmpty())) {
            return suggestedValue;
        }
        if ("CORRECTED".equals(status) && (normalized == null || normalized.isEmpty())) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private RecognitionJobVO toJobVO(AsyncJob job) {
        String outcome = null;
        if (job.getResultJson() != null) {
            try {
                JsonNode result = objectMapper.readTree(job.getResultJson());
                outcome = result.path("outcome").isMissingNode() ? null
                        : result.path("outcome").asText();
            } catch (Exception ignored) {
                outcome = null;
            }
        }
        return new RecognitionJobVO(job.getId(), job.getTargetId(), job.getStatus(),
                job.getAttemptCount() == null ? 0 : job.getAttemptCount(), outcome,
                job.getCreatedAt(), job.getFinishedAt());
    }

    private RecognitionSuggestionVO toSuggestionVO(RecognitionSuggestion suggestion) {
        return new RecognitionSuggestionVO(suggestion.getId(), suggestion.getSourceJobId(),
                suggestion.getFieldPath(), suggestion.getSuggestedValue(), suggestion.getConfidence(),
                suggestion.getStatus(), suggestion.getFinalValue(),
                suggestion.getConfirmedByCasId(), suggestion.getConfirmedAt(),
                suggestion.getCreatedAt());
    }
}
