package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.data.po.RecognitionSuggestion;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.mapper.RecognitionInvoiceMapper;
import cn.sduonline.invoice.mapper.RecognitionSuggestionMapper;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter.RecognitionResult;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter.SuggestedField;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RecognitionResultService {
    private static final Set<String> FIELDS = Set.of(
            "invoiceType", "invoiceCode", "invoiceNumber", "digitalInvoiceNo",
            "invoiceDate", "buyerName", "buyerTaxNo", "sellerName", "sellerTaxNo",
            "faceAmount");
    private static final Set<String> LANDABLE_STATES = Set.of(
            "DRAFT", "RETURNED", "PENDING_RECOGNITION");

    private final RecognitionSuggestionMapper suggestionMapper;
    private final AsyncJobMapper jobMapper;
    private final RecognitionInvoiceMapper invoiceMapper;
    private final AsyncJobClaimService claimService;
    private final ObjectMapper objectMapper;

    public RecognitionResultService(RecognitionSuggestionMapper suggestionMapper,
                                    AsyncJobMapper jobMapper,
                                    RecognitionInvoiceMapper invoiceMapper,
                                    AsyncJobClaimService claimService,
                                    ObjectMapper objectMapper) {
        this.suggestionMapper = suggestionMapper;
        this.jobMapper = jobMapper;
        this.invoiceMapper = invoiceMapper;
        this.claimService = claimService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persist(AsyncJob job, String sourceFileId, RecognitionResult result) {
        Invoice invoice = invoiceMapper.lockById(job.getOrganizationId(), job.getTargetId());
        AsyncJob currentJob = jobMapper.lockById(job.getId());
        if (!sameRunningLease(currentJob, job)) {
            throw new IllegalStateException("RECOGNITION_JOB_NOT_RUNNING");
        }
        if (invoice != null && !sourceFileId.equals(invoice.getCurrentFileId())) {
            if ("PENDING_RECOGNITION".equals(invoice.getStatus())) {
                if (invoiceMapper.restoreDraftIfPending(
                        job.getOrganizationId(), job.getTargetId()) != 1) {
                    throw new IllegalStateException("INVOICE_STATE_CHANGED");
                }
            }
            Map<String, Object> discarded = new LinkedHashMap<>();
            discarded.put("outcome", "DISCARDED_FILE_CHANGED");
            discarded.put("sourceFileId", sourceFileId);
            discarded.put("currentFileId", invoice.getCurrentFileId());
            discarded.put("suggestionCount", 0);
            succeed(job, discarded);
            return;
        }
        if (invoice == null || !LANDABLE_STATES.contains(invoice.getStatus())) {
            Map<String, Object> discarded = new LinkedHashMap<>();
            discarded.put("outcome", invoice != null && "SUBMITTED".equals(invoice.getStatus())
                    ? "DISCARDED_SUBMITTED" : "DISCARDED_STATE_CHANGED");
            discarded.put("invoiceStatus", invoice == null ? "MISSING" : invoice.getStatus());
            discarded.put("suggestionCount", 0);
            succeed(job, discarded);
            return;
        }
        suggestionMapper.delete(new LambdaQueryWrapper<RecognitionSuggestion>()
                .eq(RecognitionSuggestion::getOrganizationId, job.getOrganizationId())
                .eq(RecognitionSuggestion::getSourceJobId, job.getId()));
        Map<String, SuggestedField> fields =
                result.fields() == null ? Map.of() : result.fields();
        long suggestionCount = 0;
        if (result.available()) {
            for (Map.Entry<String, SuggestedField> entry : fields.entrySet()) {
                if (!FIELDS.contains(entry.getKey()) || entry.getValue() == null
                        || entry.getValue().value() == null) {
                    continue;
                }
                suggestionMapper.insert(RecognitionSuggestion.builder()
                        .id(UlidGenerator.next()).organizationId(job.getOrganizationId())
                        .invoiceId(job.getTargetId()).sourceJobId(job.getId())
                        .fieldPath(entry.getKey())
                        .suggestedValue(truncate(entry.getValue().value(), 10_000))
                        .confidence(normalizeConfidence(entry.getValue().confidence()))
                        .status("PENDING").build());
                suggestionCount++;
            }
        }
        if (suggestionCount > 0 && "DRAFT".equals(invoice.getStatus())) {
            if (invoiceMapper.markPendingIfDraft(job.getOrganizationId(), job.getTargetId()) != 1) {
                throw new IllegalStateException("INVOICE_STATE_CHANGED");
            }
        } else if (suggestionCount == 0 && "PENDING_RECOGNITION".equals(invoice.getStatus())
                && invoiceMapper.restoreDraftIfPending(
                job.getOrganizationId(), job.getTargetId()) != 1) {
            throw new IllegalStateException("INVOICE_STATE_CHANGED");
        }
        Map<String, Object> jobResult = new LinkedHashMap<>();
        jobResult.put("outcome", result.available() ? "SUGGESTIONS_READY" : "MANUAL_ENTRY");
        jobResult.put("rawText", truncate(result.rawText(), 100_000));
        jobResult.put("qrRaw", truncate(result.qrRaw(), 10_000));
        jobResult.put("suggestionCount", suggestionCount);
        succeed(job, jobResult);
    }

    @Transactional
    public void recordFailure(AsyncJob job, String errorCode, String errorMessage) {
        Invoice invoice = invoiceMapper.lockById(job.getOrganizationId(), job.getTargetId());
        AsyncJob currentJob = jobMapper.lockById(job.getId());
        if (!sameRunningLease(currentJob, job)) return;
        claimService.retryOrFail(currentJob, errorCode, errorMessage);
        if (invoice != null && "PENDING_RECOGNITION".equals(invoice.getStatus())) {
            invoiceMapper.restoreDraftIfPending(job.getOrganizationId(), job.getTargetId());
        }
    }

    private void succeed(AsyncJob job, Map<String, Object> jobResult) {
        try {
            if (jobMapper.succeed(job.getId(), job.getLeaseVersion(),
                    objectMapper.writeValueAsString(jobResult)) != 1) {
                throw new IllegalStateException("RECOGNITION_JOB_NOT_RUNNING");
            }
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("RECOGNITION_RESULT_SERIALIZATION_FAILED", exception);
        }
    }

    private BigDecimal normalizeConfidence(BigDecimal confidence) {
        if (confidence == null) return null;
        if (confidence.signum() < 0) return BigDecimal.ZERO;
        if (confidence.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return confidence.setScale(Math.min(4, Math.max(0, confidence.scale())),
                java.math.RoundingMode.HALF_UP);
    }

    private boolean sameRunningLease(AsyncJob current, AsyncJob claimed) {
        return current != null && "RUNNING".equals(current.getStatus())
                && current.getLeaseVersion() != null
                && current.getLeaseVersion().equals(claimed.getLeaseVersion());
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
