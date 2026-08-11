package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 发票标签关联持久化对象。
 */
@TableName("invoice_tag")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceTag {

    private String invoiceId;
    private String organizationId;
    private String tagItemId;
    private String addedByCasId;
    private Instant addedAt;
}
