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

public final class RuleDtos {
    private RuleDtos() {
    }

    public record CreateRuleSetRequest(
            @NotBlank @Size(max = 100) String name,
            boolean isDefault) {
    }

    public record UpdateRuleSetRequest(
            @Size(min = 1, max = 100) String name,
            Boolean isDefault,
            @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotNull @Min(0) Long version) {
    }

    public record RuleDefinition(
            @NotBlank @Pattern(regexp = "MAX_FACE_AMOUNT|MAX_CLAIMED_AMOUNT|ALLOWED_INVOICE_TYPES|REQUIRE_SELLER_TAX_NO|REQUIRE_BUYER_TAX_NO") String code,
            @NotBlank @Pattern(regexp = "BLOCK|WARNING|INFO") String severity,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            Set<@NotBlank @Size(max = 50) String> values) {
    }

    public record PublishRuleVersionRequest(
            @NotEmpty @Size(max = 50) List<@Valid RuleDefinition> rules,
            @NotNull Instant effectiveAt) {
    }
}
