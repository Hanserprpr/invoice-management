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
 * 审计日志持久化对象。
 */
@TableName("audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String actorCasId;
    private String action;
    private String objectType;
    private String objectId;
    private String projectId;
    private String invoiceId;
    private String batchId;
    private String beforeDigest;
    private String afterDigest;
    private String changeSummaryJson;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
}
