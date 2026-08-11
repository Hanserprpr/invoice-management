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
 * 发票文件版本持久化对象。
 */
@TableName("invoice_file_revision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceFileRevision {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String fileId;
    private Integer revisionNo;
    private String replacementReason;
    private String replacedByCasId;
    private Instant createdAt;
}
