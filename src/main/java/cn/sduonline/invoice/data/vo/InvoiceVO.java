package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceVO(
        String id, String applicationId, String invoiceType,
        String invoiceCode, String invoiceNumber, String digitalInvoiceNo,
        LocalDate invoiceDate, String buyerName, String buyerTaxNo,
        String sellerName, String sellerTaxNo, BigDecimal faceAmount,
        BigDecimal claimedAmount, String currentFileId, String expenseCategoryItemId,
        String status, String voidReason, Instant voidedAt, long version,
        Instant createdAt, Instant updatedAt,
        List<InvoiceFileRevisionVO> fileRevisions, List<AttachmentVO> attachments) {
}
