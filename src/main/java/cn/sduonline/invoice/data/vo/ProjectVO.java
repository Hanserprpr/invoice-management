package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ProjectVO(
        String id,
        String organizationId,
        String name,
        String description,
        BigDecimal budget,
        String fundingSource,
        boolean paperRequired,
        String ruleSetVersionId,
        String visibility,
        Instant startAt,
        Instant endAt,
        String status,
        long version,
        String createdByCasId,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        List<String> managerCasIds,
        List<AccessGrantVO> accessGrants) {

    public record AccessGrantVO(String casId, Set<String> accessTypes) {
    }
}
