package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;

import java.time.Instant;

public record AvailableFormVO(
        String id,
        String projectId,
        String name,
        String submissionScope,
        Instant startsAt,
        Instant endsAt,
        int maxSubmissionsPerUser,
        String formVersionId,
        int formVersionNo,
        FormSchema schema) {
}
