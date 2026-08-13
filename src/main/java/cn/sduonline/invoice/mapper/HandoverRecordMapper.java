package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.HandoverRecord;
import cn.sduonline.invoice.data.po.OrganizationMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HandoverRecordMapper extends BaseMapper<HandoverRecord> {
    @Select("SELECT * FROM organization_member WHERE organization_id=#{organizationId} AND cas_id=#{casId} FOR UPDATE")
    OrganizationMember lockMember(@Param("organizationId") String organizationId,
                                  @Param("casId") String casId);

    @Select("SELECT project_id FROM project_manager WHERE organization_id=#{organizationId} AND member_id=#{memberId} ORDER BY project_id")
    List<String> findManagedProjects(@Param("organizationId") String organizationId,
                                     @Param("memberId") String memberId);

    @Select("SELECT COUNT(*) FROM project_access WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int countAccess(@Param("organizationId") String organizationId,
                    @Param("memberId") String memberId);

    @Insert("""
            INSERT IGNORE INTO project_manager(project_id,organization_id,member_id,assigned_by_cas_id)
            SELECT project_id,organization_id,#{incomingMemberId},#{actorCasId}
            FROM project_manager WHERE organization_id=#{organizationId} AND member_id=#{outgoingMemberId}
            """)
    int copyManagers(@Param("organizationId") String organizationId,
                     @Param("outgoingMemberId") String outgoingMemberId,
                     @Param("incomingMemberId") String incomingMemberId,
                     @Param("actorCasId") String actorCasId);

    @Delete("DELETE FROM project_manager WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int deleteManagers(@Param("organizationId") String organizationId,
                       @Param("memberId") String memberId);

    @Insert("""
            INSERT IGNORE INTO project_access(project_id,organization_id,member_id,access_type,granted_by_cas_id)
            SELECT project_id,organization_id,#{incomingMemberId},access_type,#{actorCasId}
            FROM project_access WHERE organization_id=#{organizationId} AND member_id=#{outgoingMemberId}
            """)
    int copyAccess(@Param("organizationId") String organizationId,
                   @Param("outgoingMemberId") String outgoingMemberId,
                   @Param("incomingMemberId") String incomingMemberId,
                   @Param("actorCasId") String actorCasId);

    @Delete("DELETE FROM project_access WHERE organization_id=#{organizationId} AND member_id=#{memberId}")
    int deleteAccess(@Param("organizationId") String organizationId,
                     @Param("memberId") String memberId);

    @Update("""
            UPDATE organization_member SET status='INACTIVE',term_end=#{termEnd},ended_at=#{endedAt},
              version=version+1
            WHERE organization_id=#{organizationId} AND id=#{memberId} AND status='ACTIVE'
              AND version=#{version}
            """)
    int endMembership(@Param("organizationId") String organizationId,
                      @Param("memberId") String memberId, @Param("version") long version,
                      @Param("termEnd") LocalDate termEnd, @Param("endedAt") Instant endedAt);

    @Select("SELECT * FROM handover_record WHERE organization_id=#{organizationId} ORDER BY created_at DESC,id DESC")
    List<HandoverRecord> findForOrganization(@Param("organizationId") String organizationId);
}
