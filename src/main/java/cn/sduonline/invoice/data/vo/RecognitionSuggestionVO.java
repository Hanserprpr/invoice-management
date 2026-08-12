package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record RecognitionSuggestionVO(String id, String sourceJobId, String fieldPath,
                                      String suggestedValue, BigDecimal confidence,
                                      String status, String finalValue,
                                      String confirmedByCasId, Instant confirmedAt,
                                      Instant createdAt) {
}
