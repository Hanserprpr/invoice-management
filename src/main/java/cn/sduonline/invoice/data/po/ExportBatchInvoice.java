package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 导出批次发票快照持久化对象。
 */
@TableName("export_batch_invoice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportBatchInvoice {

    private String batchId;
    private String organizationId;
    private String invoiceId;
    private Integer sequenceNo;
    private BigDecimal snapshotFaceAmount;
    private BigDecimal snapshotClaimedAmount;
    private String snapshotJson;
}
