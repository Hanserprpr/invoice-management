package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ProjectManager;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectManagerMapper {

    @Delete("DELETE FROM project_manager WHERE organization_id=#{organizationId} AND project_id=#{projectId}")
    int deleteForProject(@Param("organizationId") String organizationId,
                         @Param("projectId") String projectId);

    @Insert("""
            INSERT INTO project_manager
              (project_id, organization_id, member_id, assigned_by_cas_id)
            VALUES
              (#{projectId}, #{organizationId}, #{memberId}, #{assignedByCasId})
            """)
    int insert(ProjectManager manager);
}
