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
 * 发票附件持久化对象。
 */
@TableName("attachment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String attachmentType;
    private String fileId;
    private String description;
    private String status;
    private String voidReason;
    private String voidedByCasId;
    private Instant voidedAt;
    private String createdByCasId;
    private Instant createdAt;
}
