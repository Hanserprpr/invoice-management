package cn.sduonline.invoice.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class RecognitionDtos {
    private RecognitionDtos() {
    }

    public record ConfirmSuggestionsRequest(
            @NotEmpty @Size(max = 30) List<@Valid SuggestionDecision> decisions) {
    }

    public record SuggestionDecision(
            @NotBlank @Size(max = 26) String suggestionId,
            @NotBlank @Pattern(regexp = "ACCEPTED|CORRECTED|REJECTED") String status,
            @Size(max = 2000) String finalValue) {
    }
}
