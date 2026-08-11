package cn.sduonline.invoice.data.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public final class InvoiceDtos {
    private InvoiceDtos() {
    }

    public record CreateInvoiceRequest(
            @NotBlank @Size(max = 40) String invoiceType,
            @Size(max = 50) String invoiceCode,
            @Size(max = 100) String invoiceNumber,
            @Size(max = 100) String digitalInvoiceNo,
            LocalDate invoiceDate,
            @Size(max = 200) String buyerName,
            @Size(max = 50) String buyerTaxNo,
            @Size(max = 200) String sellerName,
            @Size(max = 50) String sellerTaxNo,
            @NotNull @DecimalMin("0.00") BigDecimal faceAmount,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal claimedAmount,
            @NotBlank @Size(max = 26) String fileId,
            @Size(max = 26) String expenseCategoryItemId) {
    }

    public record UpdateInvoiceRequest(
            @NotNull @Min(0) Long version,
            @Size(max = 40) String invoiceType,
            @Size(max = 50) String invoiceCode,
            @Size(max = 100) String invoiceNumber,
            @Size(max = 100) String digitalInvoiceNo,
            LocalDate invoiceDate,
            @Size(max = 200) String buyerName,
            @Size(max = 50) String buyerTaxNo,
            @Size(max = 200) String sellerName,
            @Size(max = 50) String sellerTaxNo,
            @DecimalMin("0.00") BigDecimal faceAmount,
            @DecimalMin(value = "0.00", inclusive = false) BigDecimal claimedAmount,
            @Size(max = 26) String expenseCategoryItemId,
            Set<@Pattern(regexp = "invoiceCode|invoiceNumber|digitalInvoiceNo|invoiceDate|buyerName|buyerTaxNo|sellerName|sellerTaxNo|expenseCategoryItemId") String> clearFields) {
    }

    public record ReplaceFileRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 26) String fileId,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record AddAttachmentRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Pattern(regexp = "PAYMENT_RECORD|ORDER_DETAIL|OTHER") String attachmentType,
            @NotBlank @Size(max = 26) String fileId,
            @Size(max = 500) String description) {
    }

    public record VersionedReasonRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 500) String reason) {
    }
}
