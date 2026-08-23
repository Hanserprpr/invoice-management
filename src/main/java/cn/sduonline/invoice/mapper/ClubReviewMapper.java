package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ClubReview;
import cn.sduonline.invoice.data.vo.ReviewQueueItemVO;
import cn.sduonline.invoice.data.vo.ReviewStatsVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClubReviewMapper extends BaseMapper<ClubReview> {
    record ReviewHistoryRow(String id, String invoiceId, String reviewerCasId,
                            String reviewerName, String action, String reasonItemId,
                            String returnFieldsJson, String comment, String batchOperationId,
                            java.time.Instant createdAt) {
    }

    @Select("""
            SELECT cr.id,cr.invoice_id,cr.reviewer_cas_id,u.name reviewer_name,
              cr.action,cr.reason_item_id,cr.return_fields_json,cr.comment,
              cr.batch_operation_id,cr.created_at
            FROM club_review cr
            JOIN `user` u ON u.cas_id=cr.reviewer_cas_id
            WHERE cr.organization_id=#{organizationId} AND cr.invoice_id=#{invoiceId}
            ORDER BY cr.created_at,cr.id
            """)
    List<ReviewHistoryRow> findForInvoice(@Param("organizationId") String organizationId,
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
              i.seller_name,i.invoice_type,i.invoice_number,i.status,
              (SELECT COUNT(*) FROM invoice_precheck_result r
                WHERE r.organization_id=i.organization_id AND r.invoice_id=i.id
                  AND r.result='HIT' AND r.severity='BLOCK' AND r.resolution IS NULL
                  AND NOT EXISTS(SELECT 1 FROM invoice_precheck_result newer
                    WHERE newer.organization_id=r.organization_id AND newer.invoice_id=r.invoice_id
                      AND newer.check_type=r.check_type
                      AND COALESCE(newer.rule_code,'')=COALESCE(r.rule_code,'')
                      AND (newer.created_at>r.created_at OR (newer.created_at=r.created_at AND newer.id>r.id)))) precheck_block_count,
              (SELECT COUNT(*) FROM invoice_precheck_result r
                WHERE r.organization_id=i.organization_id AND r.invoice_id=i.id
                  AND r.result='HIT' AND r.severity='WARNING' AND r.resolution IS NULL
                  AND NOT EXISTS(SELECT 1 FROM invoice_precheck_result newer
                    WHERE newer.organization_id=r.organization_id AND newer.invoice_id=r.invoice_id
                      AND newer.check_type=r.check_type
                      AND COALESCE(newer.rule_code,'')=COALESCE(r.rule_code,'')
                      AND (newer.created_at>r.created_at OR (newer.created_at=r.created_at AND newer.id>r.id)))) precheck_warning_count,
              i.version,a.submitted_at,i.updated_at
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
              i.seller_name,i.invoice_type,i.invoice_number,i.status,
              (SELECT COUNT(*) FROM invoice_precheck_result r WHERE r.organization_id=i.organization_id
                AND r.invoice_id=i.id AND r.result='HIT' AND r.severity='BLOCK' AND r.resolution IS NULL) precheck_block_count,
              (SELECT COUNT(*) FROM invoice_precheck_result r WHERE r.organization_id=i.organization_id
                AND r.invoice_id=i.id AND r.result='HIT' AND r.severity='WARNING' AND r.resolution IS NULL) precheck_warning_count,
              i.version,a.submitted_at,i.updated_at
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

    @Select("""
            <script>
            SELECT
              COALESCE(SUM(i.status='SUBMITTED'),0) submitted,
              COALESCE(SUM(i.status='IN_REVIEW'),0) in_review,
              COALESCE(SUM(i.status='INTERNALLY_APPROVED'),0) internally_approved,
              (SELECT COUNT(DISTINCT cr.invoice_id) FROM club_review cr
                JOIN invoice ri ON ri.id=cr.invoice_id AND ri.organization_id=cr.organization_id
                JOIN application ra ON ra.id=ri.application_id AND ra.organization_id=ri.organization_id
                JOIN form_version rfv ON rfv.id=ra.form_version_id AND rfv.organization_id=ra.organization_id
                JOIN application_form raf ON raf.id=rfv.form_id AND raf.organization_id=rfv.organization_id
                WHERE cr.organization_id=#{organizationId}
                  AND cr.action IN ('APPROVE','RETURN','REJECT') AND DATE(cr.created_at)=CURRENT_DATE
                  <if test="projectId != null">AND raf.project_id=#{projectId}</if>
                  AND (#{unrestricted}=TRUE OR EXISTS(SELECT 1 FROM project_access rpa
                    JOIN organization_member rm ON rm.id=rpa.member_id AND rm.organization_id=rpa.organization_id
                    WHERE rpa.organization_id=cr.organization_id AND rpa.project_id=raf.project_id
                      AND rm.cas_id=#{actorCasId} AND rm.status='ACTIVE'
                      AND rpa.access_type IN ('REVIEW','MANAGE')))) reviewed_today,
              (SELECT COUNT(*) FROM invoice_precheck_result pr
                JOIN invoice pi ON pi.id=pr.invoice_id AND pi.organization_id=pr.organization_id
                JOIN application pa ON pa.id=pi.application_id AND pa.organization_id=pi.organization_id
                JOIN form_version pfv ON pfv.id=pa.form_version_id AND pfv.organization_id=pa.organization_id
                JOIN application_form paf ON paf.id=pfv.form_id AND paf.organization_id=pfv.organization_id
                WHERE pr.organization_id=#{organizationId} AND pr.result='HIT' AND pr.resolution IS NULL
                  <if test="projectId != null">AND paf.project_id=#{projectId}</if>
                  AND NOT EXISTS(SELECT 1 FROM invoice_precheck_result newer
                    WHERE newer.organization_id=pr.organization_id AND newer.invoice_id=pr.invoice_id
                      AND newer.check_type=pr.check_type
                      AND COALESCE(newer.rule_code,'')=COALESCE(pr.rule_code,'')
                      AND (newer.created_at>pr.created_at OR (newer.created_at=pr.created_at AND newer.id>pr.id)))
                  AND (#{unrestricted}=TRUE OR EXISTS(SELECT 1 FROM project_access ppa
                    JOIN organization_member pm ON pm.id=ppa.member_id AND pm.organization_id=ppa.organization_id
                    WHERE ppa.organization_id=pr.organization_id AND ppa.project_id=paf.project_id
                      AND pm.cas_id=#{actorCasId} AND pm.status='ACTIVE'
                      AND ppa.access_type IN ('REVIEW','MANAGE')))) precheck_issue_count
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            WHERE i.organization_id=#{organizationId}
              <if test="projectId != null">AND af.project_id=#{projectId}</if>
              AND (#{unrestricted}=TRUE OR EXISTS(SELECT 1 FROM project_access pa
                JOIN organization_member m ON m.id=pa.member_id AND m.organization_id=pa.organization_id
                WHERE pa.organization_id=i.organization_id AND pa.project_id=af.project_id
                  AND m.cas_id=#{actorCasId} AND m.status='ACTIVE'
                  AND pa.access_type IN ('REVIEW','MANAGE')))
            </script>
            """)
    ReviewStatsVO findStats(@Param("organizationId") String organizationId,
                            @Param("actorCasId") String actorCasId,
                            @Param("unrestricted") boolean unrestricted,
                            @Param("projectId") String projectId);
}
