package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ExportDtos {
    private ExportDtos() {
    }

    public record CreateExportBatchRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String batchNo,
            @NotEmpty @Size(max = 1000) List<@NotBlank String> invoiceIds) {
    }

    public record BatchVersionRequest(@NotNull @Min(0) Long version) {
    }
}
