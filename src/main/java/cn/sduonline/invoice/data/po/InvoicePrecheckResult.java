package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 发票预检结果持久化对象。
 */
@TableName("invoice_precheck_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePrecheckResult {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String ruleSetVersionId;
    private String checkType;
    private String ruleCode;
    private String severity;
    private String result;
    private String reason;
    private String matchedInvoiceId;
    private String evidenceJson;
    private String resolution;
    private String resolvedByCasId;
    private String resolutionComment;
    private Instant resolvedAt;
    private Instant createdAt;
}
