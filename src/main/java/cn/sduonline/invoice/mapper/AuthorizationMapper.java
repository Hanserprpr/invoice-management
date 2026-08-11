package cn.sduonline.invoice.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthorizationMapper {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS(
              SELECT 1
              FROM organization_member m
              JOIN organization_member_role mr
                ON mr.member_id=m.id AND mr.organization_id=m.organization_id
              JOIN role r ON r.id=mr.role_id
              JOIN role_permission rp ON rp.role_id=r.id
              JOIN permission p ON p.id=rp.permission_id
              WHERE m.organization_id=#{organizationId} AND m.cas_id=#{casId}
                AND m.status='ACTIVE'
                AND (m.term_start IS NULL OR m.term_start <= CURRENT_DATE)
                AND (m.term_end IS NULL OR m.term_end >= CURRENT_DATE)
                AND mr.effective_from <= CURRENT_TIMESTAMP(3)
                AND (mr.effective_until IS NULL OR mr.effective_until >= CURRENT_TIMESTAMP(3))
                AND p.code=#{permission}
            )
            """)
    boolean hasPermission(@Param("organizationId") String organizationId,
                          @Param("casId") String casId,
                          @Param("permission") String permission);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM project_access pa
              JOIN organization_member m ON m.id=pa.member_id AND m.organization_id=pa.organization_id
              WHERE pa.organization_id=#{organizationId} AND pa.project_id=#{projectId}
                AND m.cas_id=#{casId} AND m.status='ACTIVE' AND pa.access_type=#{accessType}
            )
            """)
    boolean hasProjectAccess(@Param("organizationId") String organizationId,
                             @Param("projectId") String projectId,
                             @Param("casId") String casId,
                             @Param("accessType") String accessType);
}
