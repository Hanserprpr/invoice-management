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
 * 异步任务持久化对象。
 */
@TableName("async_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJob {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String jobType;
    private String targetType;
    private String targetId;
    private String status;
    private Integer progress;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Long leaseVersion;
    private String requestJson;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Instant nextAttemptAt;
    private String createdByCasId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
}
