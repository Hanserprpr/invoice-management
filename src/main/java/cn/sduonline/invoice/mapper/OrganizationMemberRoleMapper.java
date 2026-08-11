package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.OrganizationMemberRole;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMemberRoleMapper {

    @Delete("DELETE FROM organization_member_role WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int deleteForMember(@Param("organizationId") String organizationId,
                        @Param("memberId") String memberId);

    @Insert("""
            INSERT INTO organization_member_role
              (member_id, organization_id, role_id, effective_from, effective_until, assigned_by_cas_id)
            VALUES
              (#{memberId}, #{organizationId}, #{roleId}, #{effectiveFrom}, #{effectiveUntil}, #{assignedByCasId})
            """)
    int insert(OrganizationMemberRole role);
}
