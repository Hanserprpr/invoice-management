package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record InvoiceTimelineEventVO(String id, String eventType, String action,
                                     String actorCasId, String status, String detail,
                                     Instant occurredAt) {
}
