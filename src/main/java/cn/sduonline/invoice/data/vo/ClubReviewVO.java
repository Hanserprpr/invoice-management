package cn.sduonline.invoice.data.vo;

import java.time.Instant;
import java.util.Set;

public record ClubReviewVO(String id, String invoiceId, String reviewerCasId, String reviewerName,
                           String action, String reasonItemId, Set<String> returnFields,
                           String comment, String batchOperationId, Instant createdAt) {
}
