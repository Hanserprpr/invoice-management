package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Project;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("""
            <script>
            SELECT DISTINCT p.* FROM project p
            WHERE p.organization_id=#{organizationId}
              <if test="status != null and status != ''">AND p.status=#{status}</if>
              <if test="clubAdmin == false">
                AND (p.visibility='ALL'
                  OR EXISTS (SELECT 1 FROM project_manager pm
                    JOIN organization_member m ON m.id=pm.member_id
                    WHERE pm.project_id=p.id AND pm.organization_id=p.organization_id
                      AND m.cas_id=#{casId} AND m.status='ACTIVE'
                      AND (m.term_start IS NULL OR m.term_start &lt;= CURRENT_DATE)
                      AND (m.term_end IS NULL OR m.term_end &gt;= CURRENT_DATE))
                  OR EXISTS (SELECT 1 FROM project_access pa
                    JOIN organization_member m ON m.id=pa.member_id
                    WHERE pa.project_id=p.id AND pa.organization_id=p.organization_id
                      AND m.cas_id=#{casId} AND m.status='ACTIVE'
                      AND (m.term_start IS NULL OR m.term_start &lt;= CURRENT_DATE)
                      AND (m.term_end IS NULL OR m.term_end &gt;= CURRENT_DATE)))
              </if>
            ORDER BY p.created_at DESC, p.id DESC
            </script>
            """)
    Page<Project> findVisible(Page<Project> page,
                              @Param("organizationId") String organizationId,
                              @Param("casId") String casId,
                              @Param("clubAdmin") boolean clubAdmin,
                              @Param("status") String status);

    @Select("""
            SELECT m.cas_id FROM project_manager pm
            JOIN organization_member m ON m.id=pm.member_id AND m.organization_id=pm.organization_id
            WHERE pm.organization_id=#{organizationId} AND pm.project_id=#{projectId}
            ORDER BY m.cas_id
            """)
    List<String> findManagerCasIds(@Param("organizationId") String organizationId,
                                   @Param("projectId") String projectId);
}
