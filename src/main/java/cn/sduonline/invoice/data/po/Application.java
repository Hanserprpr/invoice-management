package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 社员申请持久化对象。
 */
@TableName("application")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String formVersionId;
    private String applicantCasId;
    private String answersJson;
    private String status;
    private Instant submittedAt;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
