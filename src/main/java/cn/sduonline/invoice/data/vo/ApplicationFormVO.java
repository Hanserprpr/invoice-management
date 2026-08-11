package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;

import java.time.Instant;

public record ApplicationFormVO(
        String id,
        String organizationId,
        String projectId,
        String name,
        String status,
        String submissionScope,
        Instant startsAt,
        Instant endsAt,
        int maxSubmissionsPerUser,
        long version,
        int latestPublishedVersionNo,
        boolean hasUnpublishedChanges,
        FormSchema draftSchema,
        String createdByCasId,
        Instant createdAt,
        Instant updatedAt) {
}
