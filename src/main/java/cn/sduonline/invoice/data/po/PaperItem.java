package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 纸票当前状态持久化对象。
 */
@TableName("paper_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperItem {

    @TableId(value = "invoice_id", type = IdType.INPUT)
    private String invoiceId;
    private String organizationId;
    private String status;
    private Instant memberDeclaredAt;
    private String receivedByCasId;
    private Instant receivedAt;
    private String note;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
