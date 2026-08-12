package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.RecognitionSuggestion;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
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

    private final RecognitionSuggestionMapper suggestionMapper;
    private final AsyncJobMapper jobMapper;
    private final ObjectMapper objectMapper;

    public RecognitionResultService(RecognitionSuggestionMapper suggestionMapper,
                                    AsyncJobMapper jobMapper, ObjectMapper objectMapper) {
        this.suggestionMapper = suggestionMapper;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persist(AsyncJob job, RecognitionResult result) {
        suggestionMapper.delete(new LambdaQueryWrapper<RecognitionSuggestion>()
                .eq(RecognitionSuggestion::getOrganizationId, job.getOrganizationId())
                .eq(RecognitionSuggestion::getSourceJobId, job.getId()));
        Map<String, SuggestedField> fields =
                result.fields() == null ? Map.of() : result.fields();
        if (result.available()) {
            fields.entrySet().stream()
                    .filter(entry -> FIELDS.contains(entry.getKey()))
                    .filter(entry -> entry.getValue() != null && entry.getValue().value() != null)
                    .forEach(entry -> suggestionMapper.insert(RecognitionSuggestion.builder()
                            .id(UlidGenerator.next()).organizationId(job.getOrganizationId())
                            .invoiceId(job.getTargetId()).sourceJobId(job.getId())
                            .fieldPath(entry.getKey())
                            .suggestedValue(truncate(entry.getValue().value(), 10_000))
                            .confidence(normalizeConfidence(entry.getValue().confidence()))
                            .status("PENDING").build()));
        }
        Map<String, Object> jobResult = new LinkedHashMap<>();
        jobResult.put("outcome", result.available() ? "SUGGESTIONS_READY" : "MANUAL_ENTRY");
        jobResult.put("rawText", truncate(result.rawText(), 100_000));
        jobResult.put("qrRaw", truncate(result.qrRaw(), 10_000));
        jobResult.put("suggestionCount", result.available()
                ? fields.keySet().stream().filter(FIELDS::contains).count() : 0);
        try {
            jobMapper.succeed(job.getId(), objectMapper.writeValueAsString(jobResult));
        } catch (Exception exception) {
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

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
