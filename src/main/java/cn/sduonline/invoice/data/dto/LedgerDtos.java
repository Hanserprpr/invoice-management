package cn.sduonline.invoice.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public final class LedgerDtos {
    private LedgerDtos() {
    }

    public record LedgerFilter(
            @Size(max = 26) String projectId,
            @Size(max = 26) String formId,
            @Size(max = 20) String applicantCasId,
            LocalDate invoiceDateFrom,
            LocalDate invoiceDateTo,
            @DecimalMin("0.00") BigDecimal minClaimedAmount,
            @DecimalMin("0.00") BigDecimal maxClaimedAmount,
            @Size(max = 26) String expenseCategoryItemId,
            @Size(max = 20) Set<@Pattern(regexp = "DRAFT|PENDING_RECOGNITION|SUBMITTED|IN_REVIEW|RETURNED|INTERNALLY_APPROVED|REJECTED|IN_EXPORT_BATCH|VOIDED|ARCHIVED") String> statuses,
            @Size(max = 26) String tagItemId,
            @Size(max = 40) String invoiceType,
            @Size(max = 30) String paperStatus,
            @Size(max = 100) String keyword) {
    }

    public record CreateSavedFilterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Valid LedgerFilter filter,
            boolean isDefault) {
    }

    public record UpdateSavedFilterRequest(
            @Size(min = 1, max = 100) String name,
            @Valid LedgerFilter filter,
            Boolean isDefault,
            @NotNull @Min(0) Long version) {
    }
}
