package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record FileObjectVO(
        String id,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String purpose,
        String scanStatus,
        String previewFileId,
        Instant readyAt,
        Instant expiresAt,
        Instant createdAt) {
}
