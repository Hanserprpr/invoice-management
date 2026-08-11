package cn.sduonline.invoice.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class OrganizationDtos {
    private OrganizationDtos() {
    }

    public record InitialAdmin(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String casId,
            @NotBlank @Size(max = 100) String name,
            LocalDate termStart,
            LocalDate termEnd) {
    }

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String type,
            @NotNull @Valid InitialAdmin initialAdmin) {
    }

    public record UpdateOrganizationRequest(
            @Size(max = 100) String name,
            @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotNull @Min(0) Long version) {
    }

    public record CreateMemberRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String casId,
            @NotBlank @Size(max = 100) String name,
            LocalDate termStart,
            LocalDate termEnd) {
    }

    public record UpdateMemberRequest(
            @Pattern(regexp = "ACTIVE|INACTIVE|LEFT") String status,
            LocalDate termStart,
            LocalDate termEnd,
            boolean clearTermStart,
            boolean clearTermEnd,
            @NotNull @Min(0) Long version) {
    }

    public record RoleAssignment(
            @NotBlank String code,
            Instant effectiveFrom,
            Instant effectiveUntil) {
    }

    public record ProjectGrant(
            @NotBlank String projectId,
            @NotEmpty Set<@Pattern(regexp = "VIEW|SUBMIT|REVIEW|MANAGE") String> accessTypes) {
    }

    public record ReplaceRolesRequest(
            @NotNull @Min(0) Long version,
            @NotNull List<@Valid RoleAssignment> roles,
            @NotNull List<@Valid ProjectGrant> projectGrants) {
    }

    public record PageQuery(
            @Min(1) long page,
            @Min(1) @Max(100) long pageSize) {
    }
}
