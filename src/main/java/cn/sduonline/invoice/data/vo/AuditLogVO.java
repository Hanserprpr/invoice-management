package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record AuditLogVO(String id, String actorCasId, String action, String objectType,
                         String objectId, String changeSummaryJson, String requestId,
                         Instant createdAt) {
}
