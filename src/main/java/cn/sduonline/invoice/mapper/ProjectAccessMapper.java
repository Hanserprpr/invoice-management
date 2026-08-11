package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ProjectAccess;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectAccessMapper {

    @Delete("DELETE FROM project_access WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int deleteForMember(@Param("organizationId") String organizationId,
                        @Param("memberId") String memberId);

    @Insert("""
            INSERT INTO project_access
              (project_id, organization_id, member_id, access_type, granted_by_cas_id)
            VALUES
              (#{projectId}, #{organizationId}, #{memberId}, #{accessType}, #{grantedByCasId})
            """)
    int insert(ProjectAccess access);
}
