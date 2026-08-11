package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 发票持久化对象。
 */
@TableName("invoice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String applicationId;
    private String invoiceType;
    private String invoiceCode;
    private String invoiceNumber;
    private String digitalInvoiceNo;
    private LocalDate invoiceDate;
    private String buyerName;
    private String buyerTaxNo;
    private String sellerName;
    private String sellerTaxNo;
    private BigDecimal faceAmount;
    private BigDecimal claimedAmount;
    private String currentFileId;
    private String expenseCategoryItemId;
    private String internalNote;
    private String fieldSourcesJson;
    private String status;
    private String voidReason;
    private String voidedByCasId;
    private Instant voidedAt;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
