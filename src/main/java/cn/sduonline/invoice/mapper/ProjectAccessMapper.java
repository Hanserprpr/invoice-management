package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ProjectAccess;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectAccessMapper {

    record MemberProjectAccessRow(String projectId, String accessType) {
    }

    @Delete("DELETE FROM project_access WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int deleteForMember(@Param("organizationId") String organizationId,
                        @Param("memberId") String memberId);

    @Delete("DELETE FROM project_access WHERE organization_id=#{organizationId} AND project_id=#{projectId}")
    int deleteForProject(@Param("organizationId") String organizationId,
                         @Param("projectId") String projectId);

    @Insert("""
            INSERT INTO project_access
              (project_id, organization_id, member_id, access_type, granted_by_cas_id)
            VALUES
              (#{projectId}, #{organizationId}, #{memberId}, #{accessType}, #{grantedByCasId})
            """)
    int insert(ProjectAccess access);

    @Select("""
            SELECT CONCAT(m.cas_id, ':', pa.access_type) FROM project_access pa
            JOIN organization_member m ON m.id=pa.member_id AND m.organization_id=pa.organization_id
            WHERE pa.organization_id=#{organizationId} AND pa.project_id=#{projectId}
            ORDER BY m.cas_id, pa.access_type
            """)
    List<String> findAccessBindings(@Param("organizationId") String organizationId,
                                    @Param("projectId") String projectId);

    @Select("""
            SELECT project_id,access_type FROM project_access
            WHERE organization_id=#{organizationId} AND member_id=#{memberId}
            ORDER BY project_id,access_type
            """)
    List<MemberProjectAccessRow> findForMember(@Param("organizationId") String organizationId,
                                                @Param("memberId") String memberId);
}
