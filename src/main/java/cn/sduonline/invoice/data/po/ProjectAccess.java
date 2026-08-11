package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 项目访问授权持久化对象。
 */
@TableName("project_access")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAccess {

    private String projectId;
    private String organizationId;
    private String memberId;
    private String accessType;
    private String grantedByCasId;
    private Instant grantedAt;
}
