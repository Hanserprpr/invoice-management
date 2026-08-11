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
 * 换届交接记录持久化对象。
 */
@TableName("handover_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverRecord {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String outgoingMemberId;
    private String incomingMemberId;
    private String performedByCasId;
    private String snapshotJson;
    private String comment;
    private Instant createdAt;
}
