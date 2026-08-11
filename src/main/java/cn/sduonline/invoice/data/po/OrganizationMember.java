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
import java.time.LocalDate;

/**
 * 社团成员持久化对象。
 */
@TableName("organization_member")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMember {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String casId;
    private String status;
    private LocalDate termStart;
    private LocalDate termEnd;
    private Instant joinedAt;
    private Instant endedAt;
    @Version
    private Long version;
}
