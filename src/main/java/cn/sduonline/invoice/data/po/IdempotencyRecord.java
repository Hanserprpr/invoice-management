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
 * 幂等记录持久化对象。
 */
@TableName("idempotency_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String casId;
    private String idempotencyKey;
    private String requestMethod;
    private String requestPath;
    private String requestHash;
    private Integer responseStatus;
    private String responseBody;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
}
