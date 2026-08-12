package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PaperDtos {
    private PaperDtos() {
    }

    public record PaperVersionRequest(@NotNull @Min(0) Long version) {
    }

    public record PaperStateRequest(
            @NotBlank @Pattern(regexp = "RETURNED_TO_MEMBER|TRANSFERRED_EXTERNAL|ARCHIVED|EXCEPTION|PENDING_DELIVERY") String status,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Min(0) Long version) {
    }

    public record PaperScanRequest(@NotBlank @Size(max = 2000) String rawPayload) {
    }
}
