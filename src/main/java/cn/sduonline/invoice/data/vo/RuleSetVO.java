package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.RuleDtos.RuleDefinition;

import java.time.Instant;
import java.util.List;

public record RuleSetVO(
        String id,
        String name,
        boolean isDefault,
        String status,
        long version,
        String createdByCasId,
        Instant createdAt,
        Instant updatedAt,
        List<VersionVO> versions) {

    public record VersionVO(String id, int versionNo, List<RuleDefinition> rules,
                            Instant effectiveAt, String publishedByCasId, Instant publishedAt) {
    }
}
