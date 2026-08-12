package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record RecognitionJobVO(String id, String invoiceId, String status,
                               int attemptCount, String outcome, Instant createdAt,
                               Instant finishedAt) {
}
