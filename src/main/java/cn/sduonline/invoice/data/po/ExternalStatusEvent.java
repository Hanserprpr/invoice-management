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
 * 平台外状态事件持久化对象。
 */
@TableName("external_status_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalStatusEvent {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String batchId;
    private String actorCasId;
    private String eventType;
    private String status;
    private String correctionOfEventId;
    private String comment;
    private Instant createdAt;
}
