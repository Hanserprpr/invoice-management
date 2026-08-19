package cn.sduonline.invoice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProjectLifecycleMapper {

    @Select("""
            SELECT COUNT(*)
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            WHERE i.organization_id=#{organizationId} AND af.project_id=#{projectId}
              AND i.status NOT IN ('REJECTED','VOIDED','ARCHIVED')
            """)
    int countUnresolvedInvoices(@Param("organizationId") String organizationId,
                                @Param("projectId") String projectId);

    @Select("""
            SELECT DISTINCT a.id
            FROM application a
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN invoice i ON i.application_id=a.id AND i.organization_id=a.organization_id
            WHERE a.organization_id=#{organizationId} AND af.project_id=#{projectId}
              AND i.status IN ('REJECTED','VOIDED')
            ORDER BY a.id
            """)
    List<String> findTerminalApplicationIds(@Param("organizationId") String organizationId,
                                            @Param("projectId") String projectId);

    @Update("""
            UPDATE invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            SET i.status='ARCHIVED', i.version=i.version+1
            WHERE i.organization_id=#{organizationId} AND af.project_id=#{projectId}
              AND i.status IN ('REJECTED','VOIDED')
            """)
    int archiveTerminalInvoices(@Param("organizationId") String organizationId,
                                @Param("projectId") String projectId);
}
