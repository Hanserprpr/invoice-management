package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ExternalStatusDtos {
    private ExternalStatusDtos() { }

    public record CreateExternalEventRequest(
            @NotBlank @Pattern(regexp = "STATUS_CHANGE|COMMENT") String eventType,
            @NotBlank @Pattern(regexp = "PENDING_EXTERNAL|SUBMITTED_EXTERNAL|RETURNED_EXTERNAL|COMPLETED|CANCELLED|ARCHIVED") String status,
            @Size(max = 1000) String comment,
            @Size(max = 20) List<@NotBlank String> attachmentFileIds,
            @NotNull @Min(0) Long batchVersion) {
    }

    public record CorrectExternalEventRequest(
            @NotBlank @Pattern(regexp = "PENDING_EXTERNAL|SUBMITTED_EXTERNAL|RETURNED_EXTERNAL|COMPLETED|CANCELLED|ARCHIVED") String status,
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 20) List<@NotBlank String> attachmentFileIds,
            @NotNull @Min(0) Long batchVersion) {
    }
}
