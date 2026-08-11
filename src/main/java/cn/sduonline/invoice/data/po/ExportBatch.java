package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 导出批次持久化对象。
 */
@TableName("export_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportBatch {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String projectId;
    private String batchNo;
    private Integer revisionNo;
    private String status;
    private Integer invoiceCount;
    private BigDecimal totalFaceAmount;
    private BigDecimal totalClaimedAmount;
    private String snapshotJson;
    @Version
    private Long version;
    private String createdByCasId;
    private Instant createdAt;
    private Instant generatedAt;
    private Instant firstDownloadedAt;
    private Instant completedAt;
    private Instant archivedAt;
    private Instant cancelledAt;
}
