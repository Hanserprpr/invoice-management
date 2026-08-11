package cn.sduonline.invoice.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberRoleQueryMapper {
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT r.code
            FROM organization_member_role mr JOIN role r ON r.id=mr.role_id
            WHERE mr.organization_id=#{organizationId} AND mr.member_id=#{memberId}
              AND mr.effective_from <= CURRENT_TIMESTAMP(3)
              AND (mr.effective_until IS NULL OR mr.effective_until >= CURRENT_TIMESTAMP(3))
            ORDER BY r.code
            """)
    List<String> findActiveRoleCodes(@Param("organizationId") String organizationId,
                                     @Param("memberId") String memberId);
}
