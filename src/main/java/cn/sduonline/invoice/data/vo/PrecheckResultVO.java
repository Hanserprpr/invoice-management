package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record PrecheckResultVO(
        String id,
        String invoiceId,
        String ruleSetVersionId,
        String checkType,
        String ruleCode,
        String severity,
        String result,
        String reason,
        String resolution,
        String resolvedByCasId,
        String resolutionComment,
        Instant resolvedAt,
        Instant createdAt) {
}
