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
 * 纸票状态事件持久化对象。
 */
@TableName("paper_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperEvent {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String fromStatus;
    private String toStatus;
    private String eventType;
    private String reason;
    private String actorCasId;
    private String scanEventId;
    private Instant createdAt;
}
