package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record NotificationVO(String id, String notificationType, String title, String content,
                             String targetType, String targetId, String status,
                             Instant readAt, Instant createdAt) {
}
