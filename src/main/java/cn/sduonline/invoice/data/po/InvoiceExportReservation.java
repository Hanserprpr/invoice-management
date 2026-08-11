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
 * 发票导出占用持久化对象。
 */
@TableName("invoice_export_reservation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceExportReservation {

    @TableId(value = "invoice_id", type = IdType.INPUT)
    private String invoiceId;
    private String organizationId;
    private String batchId;
    private Instant reservedAt;
}
