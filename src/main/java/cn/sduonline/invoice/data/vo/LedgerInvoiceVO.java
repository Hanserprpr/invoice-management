package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LedgerInvoiceVO(
        String invoiceId, String applicationId, String projectId, String projectName,
        String formId, String formName, String applicantCasId, String applicantName,
        String invoiceType, String invoiceCode, String invoiceNumber, String digitalInvoiceNo,
        LocalDate invoiceDate, String sellerName, BigDecimal faceAmount, BigDecimal claimedAmount,
        String expenseCategoryItemId, String expenseCategoryName, String status,
        String paperStatus, long version, Instant updatedAt) {
}
