package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.OrganizationMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import cn.sduonline.invoice.data.po.Organization;

/**
 * 社团成员数据访问。
 */
@Mapper
public interface OrganizationMemberMapper extends BaseMapper<OrganizationMember> {

    /**
     * 租户入口校验必须在租户上下文建立前执行，因此仅此查询绕过租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM organization_member
                WHERE organization_id = #{organizationId}
                  AND cas_id = #{casId}
                  AND status = 'ACTIVE'
                  AND (term_start IS NULL OR term_start <= CURRENT_DATE)
                  AND (term_end IS NULL OR term_end >= CURRENT_DATE)
            )
            """)
    boolean hasActiveMembership(
            @Param("organizationId") String organizationId,
            @Param("casId") String casId
    );

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT o.*
            FROM organization o
            JOIN organization_member m ON m.organization_id = o.id
            WHERE m.cas_id = #{casId}
              AND m.status = 'ACTIVE'
              AND (m.term_start IS NULL OR m.term_start <= CURRENT_DATE)
              AND (m.term_end IS NULL OR m.term_end >= CURRENT_DATE)
            ORDER BY o.name, o.id
            """)
    List<Organization> findOrganizationsForUser(@Param("casId") String casId);

    @Select("""
            SELECT * FROM organization_member
            WHERE organization_id=#{organizationId} AND cas_id=#{casId}
            """)
    OrganizationMember findByOrganizationAndCasId(
            @Param("organizationId") String organizationId,
            @Param("casId") String casId);

    @Update("""
            UPDATE organization_member
            SET version = version + 1
            WHERE organization_id=#{organizationId} AND cas_id=#{casId} AND version=#{version}
            """)
    int bumpVersion(@Param("organizationId") String organizationId,
                    @Param("casId") String casId,
                    @Param("version") long version);
}
