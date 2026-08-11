package cn.sduonline.invoice.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class ReviewDtos {
    private ReviewDtos() {
    }

    public record StartReviewRequest(@NotNull @Min(0) Long version) {
    }

    public record ApproveReviewRequest(
            @NotNull @Min(0) Long version,
            @Size(max = 26) String expenseCategoryItemId,
            @Size(max = 1000) String internalNote,
            @Size(max = 50) Set<@Size(max = 26) String> tagItemIds) {
    }

    public record ReturnReviewRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 26) String reasonItemId,
            @Size(max = 1000) String comment,
            @NotEmpty @Size(max = 20) Set<@Pattern(regexp = "invoiceType|invoiceCode|invoiceNumber|digitalInvoiceNo|invoiceDate|buyerName|buyerTaxNo|sellerName|sellerTaxNo|faceAmount|claimedAmount|currentFile|expenseCategoryItemId|attachments") String> returnFields) {
    }

    public record RejectReviewRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 26) String reasonItemId,
            @Size(max = 1000) String comment) {
    }

    public record BatchApproveItem(
            @NotBlank @Size(max = 26) String invoiceId,
            @NotNull @Min(0) Long version) {
    }

    public record BatchApproveRequest(
            @NotEmpty @Size(max = 100) List<@Valid BatchApproveItem> invoices) {
    }
}
