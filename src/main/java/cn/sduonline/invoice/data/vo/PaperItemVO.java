package cn.sduonline.invoice.data.vo;

import java.time.Instant;
import java.util.List;

public record PaperItemVO(String invoiceId, String projectId, String status,
                          Instant memberDeclaredAt, String receivedByCasId, Instant receivedAt,
                          String note, long version, Instant createdAt, Instant updatedAt,
                          List<EventVO> events) {
    public record EventVO(String id, String fromStatus, String toStatus, String eventType,
                          String reason, String actorCasId, String scanEventId, Instant createdAt) {
    }
}
