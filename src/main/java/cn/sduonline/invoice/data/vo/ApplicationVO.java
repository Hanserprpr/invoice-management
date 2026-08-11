package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record ApplicationVO(
        String id,
        String organizationId,
        String formId,
        String formName,
        String formVersionId,
        int formVersionNo,
        String applicantCasId,
        JsonNode answers,
        FormSchema schema,
        String status,
        long version,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt) {
}
