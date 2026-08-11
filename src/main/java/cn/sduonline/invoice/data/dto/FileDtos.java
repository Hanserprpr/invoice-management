package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FileDtos {
    private FileDtos() {
    }

    public record RegisterFileRequest(
            @NotBlank @Size(max = 255) String originalName,
            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Min(1) @Max(52_428_800) Long sizeBytes,
            @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") String sha256,
            @NotBlank @Pattern(regexp = "INVOICE_ORIGINAL|PAYMENT_RECORD|ORDER_DETAIL|FORM_ATTACHMENT|OTHER")
            String purpose) {
    }

    public record InspectFileRequest(
            @NotBlank @Pattern(regexp = "READY|REJECTED|FAILED") String status,
            @Size(max = 26) String previewFileId) {
    }

    public record CompleteUploadRequest(
            @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") String sha256) {
    }
}
