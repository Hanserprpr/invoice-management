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
 * 导出产物持久化对象。
 */
@TableName("export_artifact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportArtifact {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String batchId;
    private String jobId;
    private String artifactType;
    private String fileId;
    private String relativePath;
    private String sha256;
    private Instant createdAt;
}
