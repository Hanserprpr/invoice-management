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
 * 通知持久化对象。
 */
@TableName("notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String recipientCasId;
    private String organizationId;
    private String notificationType;
    private String title;
    private String content;
    private String targetType;
    private String targetId;
    private String deduplicationKey;
    private String status;
    private Instant readAt;
    private Instant createdAt;
}
