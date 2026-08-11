package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Application;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApplicationMapper extends BaseMapper<Application> {

    @Select("""
            SELECT a.* FROM application a
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            WHERE a.organization_id=#{organizationId} AND fv.form_id=#{formId}
              AND a.applicant_cas_id=#{casId} AND a.status IN ('DRAFT','RETURNED')
            ORDER BY a.created_at DESC LIMIT 1
            """)
    Application findEditableForApplicant(@Param("organizationId") String organizationId,
                                         @Param("formId") String formId,
                                         @Param("casId") String casId);

    @Select("""
            SELECT COUNT(*) FROM application a
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            WHERE a.organization_id=#{organizationId} AND fv.form_id=#{formId}
              AND a.applicant_cas_id=#{casId} AND a.status != 'DRAFT'
            """)
    int countSubmittedForApplicant(@Param("organizationId") String organizationId,
                                   @Param("formId") String formId,
                                   @Param("casId") String casId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT cas_id FROM `user` WHERE cas_id=#{casId} FOR UPDATE")
    String lockApplicant(@Param("casId") String casId);

    @Update("""
            UPDATE application SET status=CASE
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId})
                   AND NOT EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status!='ARCHIVED') THEN 'COMPLETED'
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status='RETURNED') THEN 'RETURNED'
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId})
                   AND NOT EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status NOT IN ('REJECTED','VOIDED'))
                    THEN 'REJECTED'
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId}
                    AND i.status IN ('INTERNALLY_APPROVED','IN_EXPORT_BATCH','ARCHIVED'))
                   AND EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status IN ('REJECTED','VOIDED'))
                    THEN 'PARTIALLY_APPROVED'
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status!='VOIDED')
                   AND NOT EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId}
                    AND i.status NOT IN ('INTERNALLY_APPROVED','IN_EXPORT_BATCH','ARCHIVED','VOIDED'))
                    THEN 'APPROVED'
              WHEN EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status!='VOIDED')
                   AND NOT EXISTS (SELECT 1 FROM invoice i WHERE i.organization_id=#{organizationId}
                    AND i.application_id=#{applicationId} AND i.status NOT IN ('SUBMITTED','VOIDED'))
                    THEN 'SUBMITTED'
              ELSE 'PROCESSING' END,
              version=version+1
            WHERE organization_id=#{organizationId} AND id=#{applicationId} AND status!='DRAFT'
            """)
    int refreshDerivedStatus(@Param("organizationId") String organizationId,
                             @Param("applicationId") String applicationId);
}
