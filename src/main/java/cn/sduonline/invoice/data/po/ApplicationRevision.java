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
 * 申请修改历史持久化对象。
 */
@TableName("application_revision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRevision {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String applicationId;
    private Integer revisionNo;
    private String answersJson;
    private String changedFieldsJson;
    private String changeReason;
    private String actorCasId;
    private Instant createdAt;
}
