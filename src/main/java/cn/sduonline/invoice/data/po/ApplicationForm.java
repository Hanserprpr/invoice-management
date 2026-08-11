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
 * 申请表持久化对象。
 */
@TableName("application_form")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationForm {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String projectId;
    private String name;
    private String status;
    private String submissionScope;
    private Instant startsAt;
    private Instant endsAt;
    private Integer maxSubmissionsPerUser;
    @Version
    private Long version;
    private String createdByCasId;
    private Instant createdAt;
    private Instant updatedAt;
}
