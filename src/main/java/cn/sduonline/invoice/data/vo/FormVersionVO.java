package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record FormVersionVO(
        String id,
        String formId,
        int versionNo,
        FormSchema schema,
        JsonNode dictionarySnapshot,
        String publishedByCasId,
        Instant publishedAt) {
}
