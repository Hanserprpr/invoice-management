package cn.sduonline.invoice.data.vo;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record MemberVO(
        String id,
        String organizationId,
        String casId,
        String name,
        String status,
        LocalDate termStart,
        LocalDate termEnd,
        long version,
        List<String> roles,
        List<RoleAssignmentVO> roleAssignments,
        List<ProjectGrantVO> projectGrants) {

    public record RoleAssignmentVO(String code, Instant effectiveFrom, Instant effectiveUntil) {
    }

    public record ProjectGrantVO(String projectId, Set<String> accessTypes) {
    }
}
