package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ClubReview;
import cn.sduonline.invoice.data.vo.ReviewQueueItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClubReviewMapper extends BaseMapper<ClubReview> {
    @Select("SELECT * FROM club_review WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY created_at,id")
    List<ClubReview> findForInvoice(@Param("organizationId") String organizationId,
                                    @Param("invoiceId") String invoiceId);

    @Select("""
            SELECT return_fields_json FROM club_review
            WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} AND action='RETURN'
            ORDER BY created_at DESC,id DESC LIMIT 1
            """)
    String findLatestReturnFields(@Param("organizationId") String organizationId,
                                  @Param("invoiceId") String invoiceId);

    @Select("""
            <script>
            SELECT i.id invoice_id,p.id project_id,p.name project_name,
              af.id form_id,af.name form_name,a.id application_id,
              a.applicant_cas_id,u.name applicant_name,i.face_amount,i.claimed_amount,
              i.invoice_type,i.invoice_number,i.status,i.version,a.submitted_at,i.updated_at
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN `user` u ON u.cas_id=a.applicant_cas_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE i.organization_id=#{organizationId}
              <if test="projectId != null">AND p.id=#{projectId}</if>
              <if test="status != null">AND i.status=#{status}</if>
              <if test="applicantCasId != null">AND a.applicant_cas_id=#{applicantCasId}</if>
              AND (#{unrestricted}=TRUE OR EXISTS(
                SELECT 1 FROM project_access pa
                JOIN organization_member m ON m.id=pa.member_id AND m.organization_id=pa.organization_id
                WHERE pa.organization_id=i.organization_id AND pa.project_id=p.id
                  AND m.cas_id=#{actorCasId} AND m.status='ACTIVE'
                  AND pa.access_type IN ('REVIEW','MANAGE')
                  AND (m.term_start IS NULL OR m.term_start &lt;= CURRENT_DATE)
                  AND (m.term_end IS NULL OR m.term_end &gt;= CURRENT_DATE)))
            ORDER BY i.updated_at DESC,i.id DESC
            </script>
            """)
    IPage<ReviewQueueItemVO> findQueue(Page<?> page,
                                      @Param("organizationId") String organizationId,
                                      @Param("actorCasId") String actorCasId,
                                      @Param("unrestricted") boolean unrestricted,
                                      @Param("projectId") String projectId,
                                      @Param("status") String status,
                                      @Param("applicantCasId") String applicantCasId);

    @Select("""
            SELECT i.id invoice_id,p.id project_id,p.name project_name,
              af.id form_id,af.name form_name,a.id application_id,
              a.applicant_cas_id,u.name applicant_name,i.face_amount,i.claimed_amount,
              i.invoice_type,i.invoice_number,i.status,i.version,a.submitted_at,i.updated_at
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN `user` u ON u.cas_id=a.applicant_cas_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE i.organization_id=#{organizationId} AND i.id=#{invoiceId}
            """)
    ReviewQueueItemVO findSummary(@Param("organizationId") String organizationId,
                                  @Param("invoiceId") String invoiceId);
}
