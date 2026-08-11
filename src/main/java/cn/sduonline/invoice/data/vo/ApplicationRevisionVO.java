package cn.sduonline.invoice.data.vo;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Set;

public record ApplicationRevisionVO(
        int revisionNo,
        JsonNode answers,
        Set<String> changedFields,
        String changeReason,
        String actorCasId,
        Instant createdAt) {
}
