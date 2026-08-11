package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 成员角色关联持久化对象。
 */
@TableName("organization_member_role")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberRole {

    private String memberId;
    private String organizationId;
    private Long roleId;
    private Instant effectiveFrom;
    private Instant effectiveUntil;
    private String assignedByCasId;
}
