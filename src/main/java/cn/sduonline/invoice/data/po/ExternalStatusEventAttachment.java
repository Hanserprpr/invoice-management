package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台外状态事件附件持久化对象。
 */
@TableName("external_status_event_attachment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalStatusEventAttachment {

    private String eventId;
    private String organizationId;
    private String fileId;
}
