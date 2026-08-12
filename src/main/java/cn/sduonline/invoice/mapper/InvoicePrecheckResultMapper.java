package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.InvoicePrecheckResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InvoicePrecheckResultMapper extends BaseMapper<InvoicePrecheckResult> {
    record DuplicateMatch(String invoiceId, String organizationId) {
    }

    record InvoiceProjectContext(String projectId, String ruleSetVersionId) {
    }

    @Select("""
            SELECT COUNT(*) FROM invoice_precheck_result r
            JOIN invoice i ON i.id=r.invoice_id AND i.organization_id=r.organization_id
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE r.organization_id=#{organizationId} AND r.invoice_id=#{invoiceId}
              AND r.severity='BLOCK' AND r.result='HIT'
              AND (r.resolution IS NULL OR r.resolution NOT IN ('FALSE_POSITIVE','ACCEPTED_RISK'))
              AND (r.check_type<>'RULE' OR r.rule_set_version_id<=>p.rule_set_version_id)
              AND NOT EXISTS (
                SELECT 1 FROM invoice_precheck_result newer
                WHERE newer.organization_id=r.organization_id AND newer.invoice_id=r.invoice_id
                  AND newer.check_type=r.check_type
                  AND COALESCE(newer.rule_code,'')=COALESCE(r.rule_code,'')
                  AND (newer.created_at>r.created_at
                    OR (newer.created_at=r.created_at AND newer.id>r.id)))
            """)
    int countUnresolvedBlocks(@Param("organizationId") String organizationId,
                              @Param("invoiceId") String invoiceId);

    @Select("""
            SELECT p.id project_id,p.rule_set_version_id
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE i.organization_id=#{organizationId} AND i.id=#{invoiceId}
            """)
    InvoiceProjectContext findContext(@Param("organizationId") String organizationId,
                                      @Param("invoiceId") String invoiceId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id invoice_id,organization_id FROM invoice
            WHERE id<>#{invoiceId} AND digital_invoice_no=#{digitalInvoiceNo}
              AND status IN ('SUBMITTED','IN_REVIEW','RETURNED','INTERNALLY_APPROVED')
            ORDER BY (organization_id=#{organizationId}) DESC,created_at LIMIT 1
            """)
    DuplicateMatch findExactDigital(@Param("invoiceId") String invoiceId,
                                    @Param("organizationId") String organizationId,
                                    @Param("digitalInvoiceNo") String digitalInvoiceNo);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id invoice_id,organization_id FROM invoice
            WHERE id<>#{invoiceId} AND invoice_code=#{invoiceCode}
              AND invoice_number=#{invoiceNumber}
              AND status IN ('SUBMITTED','IN_REVIEW','RETURNED','INTERNALLY_APPROVED')
            ORDER BY (organization_id=#{organizationId}) DESC,created_at LIMIT 1
            """)
    DuplicateMatch findExactCodeNumber(@Param("invoiceId") String invoiceId,
                                       @Param("organizationId") String organizationId,
                                       @Param("invoiceCode") String invoiceCode,
                                       @Param("invoiceNumber") String invoiceNumber);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT i.id invoice_id,i.organization_id
            FROM invoice i
            JOIN file_object f ON f.id=i.current_file_id
            JOIN file_object own ON own.id=#{fileId}
            WHERE i.id<>#{invoiceId}
              AND i.status IN ('SUBMITTED','IN_REVIEW','RETURNED','INTERNALLY_APPROVED')
              AND f.sha256=own.sha256 AND f.scan_status='READY'
            ORDER BY (i.organization_id=#{organizationId}) DESC,i.created_at LIMIT 1
            """)
    DuplicateMatch findExactFile(@Param("invoiceId") String invoiceId,
                                 @Param("organizationId") String organizationId,
                                 @Param("fileId") String fileId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id invoice_id,organization_id FROM invoice
            WHERE id<>#{invoiceId}
              AND status IN ('SUBMITTED','IN_REVIEW','RETURNED','INTERNALLY_APPROVED')
              AND seller_tax_no=#{sellerTaxNo} AND invoice_date=#{invoiceDate}
              AND face_amount=#{faceAmount}
            ORDER BY (organization_id=#{organizationId}) DESC,created_at LIMIT 1
            """)
    DuplicateMatch findSuspected(@Param("invoiceId") String invoiceId,
                                 @Param("organizationId") String organizationId,
                                 @Param("sellerTaxNo") String sellerTaxNo,
                                 @Param("invoiceDate") LocalDate invoiceDate,
                                 @Param("faceAmount") BigDecimal faceAmount);

    @Select("SELECT * FROM invoice_precheck_result WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY created_at DESC,id DESC")
    List<InvoicePrecheckResult> findForInvoice(@Param("organizationId") String organizationId,
                                               @Param("invoiceId") String invoiceId);

    @Update("""
            UPDATE invoice_precheck_result SET resolution=#{resolution},
              resolved_by_cas_id=#{actorCasId},resolution_comment=#{comment},resolved_at=#{resolvedAt}
            WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} AND id=#{resultId}
              AND result='HIT' AND resolution IS NULL
            """)
    int resolve(@Param("organizationId") String organizationId,
                @Param("invoiceId") String invoiceId,
                @Param("resultId") String resultId,
                @Param("resolution") String resolution,
                @Param("actorCasId") String actorCasId,
                @Param("comment") String comment,
                @Param("resolvedAt") Instant resolvedAt);
}
