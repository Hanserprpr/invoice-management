package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PrecheckDtos {
    private PrecheckDtos() {
    }

    public record ResolvePrecheckRequest(
            @NotNull @Pattern(regexp = "CONFIRMED|FALSE_POSITIVE|ACCEPTED_RISK") String resolution,
            @NotBlank @Size(max = 500) String comment) {
    }
}
