package cn.sduonline.invoice.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record AccessGrant(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String casId,
            @NotEmpty Set<@Pattern(regexp = "VIEW|SUBMIT|REVIEW|MANAGE") String> accessTypes) {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 10000) String description,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal budget,
            @Size(max = 200) String fundingSource,
            Boolean paperRequired,
            @NotBlank @Pattern(regexp = "ALL|AUTHORIZED") String visibility,
            Instant startAt,
            Instant endAt,
            @NotEmpty List<@Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String> managerCasIds,
            @NotNull List<@Valid AccessGrant> accessGrants) {
    }

    public record UpdateProjectRequest(
            @Size(min = 1, max = 150) String name,
            @Size(max = 10000) String description,
            boolean clearDescription,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal budget,
            boolean clearBudget,
            @Size(max = 200) String fundingSource,
            boolean clearFundingSource,
            Boolean paperRequired,
            @Pattern(regexp = "ALL|AUTHORIZED") String visibility,
            Instant startAt,
            Instant endAt,
            boolean clearStartAt,
            boolean clearEndAt,
            List<@Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String> managerCasIds,
            List<@Valid AccessGrant> accessGrants,
            @NotNull @Min(0) Long version) {
    }

    public record ChangeStateRequest(@NotNull @Min(0) Long version) {
    }
}
