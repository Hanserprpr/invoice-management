package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String digitalInvoiceNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate invoiceDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String buyerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String buyerTaxNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sellerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sellerTaxNo;
    private BigDecimal faceAmount;
    private BigDecimal claimedAmount;
    private String currentFileId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String expenseCategoryItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String internalNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fieldSourcesJson;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidedByCasId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant voidedAt;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
