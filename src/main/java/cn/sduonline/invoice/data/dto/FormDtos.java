package cn.sduonline.invoice.data.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

public final class FormDtos {
    private FormDtos() {
    }

    public record FormSchema(
            @NotNull @Size(max = 100) List<@Valid FormField> fields) {
    }

    public record FormField(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String key,
            @NotBlank @Pattern(regexp = "TEXT|TEXTAREA|NUMBER|MONEY|DATE|SINGLE_SELECT|MULTI_SELECT|PERSON|INVOICE|ATTACHMENT|NOTICE") String type,
            @NotBlank @Size(max = 150) String label,
            @NotNull Boolean required,
            @Size(max = 500) String helpText,
            @Valid Condition visibleWhen,
            @Valid Condition requiredWhen,
            List<@Valid FieldOption> options,
            BigDecimal minValue,
            BigDecimal maxValue,
            @Valid FileRule fileRule) {
    }

    public record Condition(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String fieldKey,
            @NotBlank @Pattern(regexp = "EQUALS|NOT_EQUALS|IN|NOT_EMPTY") String operator,
            JsonNode value) {
    }

    public record FieldOption(
            @NotBlank @Size(max = 100) String value,
            @NotBlank @Size(max = 150) String label) {
    }

    public record FileRule(
            @NotNull @Min(1) @Max(10) Integer maxFiles,
            @NotNull @Min(1) @Max(52428800) Long maxSizeBytes,
            @NotEmpty Set<@Pattern(regexp = "^[a-z0-9.+-]+/[a-z0-9.*+_-]+$") String> allowedContentTypes) {
    }

    public record CreateFormRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "ALL_MEMBERS|PROJECT_AUTHORIZED") String submissionScope,
            Instant startsAt,
            Instant endsAt,
            @NotNull @Min(1) @Max(100) Integer maxSubmissionsPerUser,
            @NotNull @Valid FormSchema schema) {
    }

    public record UpdateFormRequest(
            @Size(min = 1, max = 150) String name,
            @Pattern(regexp = "ALL_MEMBERS|PROJECT_AUTHORIZED") String submissionScope,
            Instant startsAt,
            Instant endsAt,
            boolean clearStartsAt,
            boolean clearEndsAt,
            @Min(1) @Max(100) Integer maxSubmissionsPerUser,
            @Valid FormSchema schema,
            @NotNull @Min(0) Long version) {
    }

    public record FormVersionRequest(@NotNull @Min(0) Long version) {
    }

    public record CopyFormRequest(
            @NotBlank String sourceFormId,
            @NotBlank @Size(max = 150) String name) {
    }
}
