package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record ReviewQueueItemVO(String invoiceId, String projectId, String projectName,
                                String formId, String formName, String applicationId,
                                String applicantCasId, String applicantName,
                                BigDecimal faceAmount, BigDecimal claimedAmount,
                                String sellerName, String invoiceType, String invoiceNumber, String status,
                                long precheckBlockCount, long precheckWarningCount,
                                long version, Instant submittedAt, Instant updatedAt) {
}
