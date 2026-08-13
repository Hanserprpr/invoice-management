package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class HandoverDtos {
    private HandoverDtos() { }

    public record CreateHandoverRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String outgoingCasId,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String incomingCasId,
            @NotNull @Min(0) Long outgoingVersion,
            @Size(max = 1000) String comment) {
    }
}
