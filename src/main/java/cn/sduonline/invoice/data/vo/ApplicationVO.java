package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;

import java.time.Instant;
import java.util.Map;

public record ApplicationVO(
        String id,
        String organizationId,
        String formId,
        String formName,
        String formVersionId,
        int formVersionNo,
        String applicantCasId,
        Map<String, Object> answers,
        FormSchema schema,
        String status,
        long version,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {
}
