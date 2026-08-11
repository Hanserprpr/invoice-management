package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public final class ApplicationDtos {
    private ApplicationDtos() {
    }

    public record SaveDraftRequest(
            @NotNull JsonNode answers,
            @NotNull @Min(0) Long version) {
    }

    public record SubmitRequest(@NotNull @Min(0) Long version) {
    }
}
