package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record AttachmentVO(String id, String attachmentType, String fileId,
                           String description, String status, String voidReason,
                           Instant voidedAt, Instant createdAt) {
}
