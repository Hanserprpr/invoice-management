package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 项目负责人关联持久化对象。
 */
@TableName("project_manager")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectManager {

    private String projectId;
    private String organizationId;
    private String memberId;
    private String assignedByCasId;
    private Instant assignedAt;
}
