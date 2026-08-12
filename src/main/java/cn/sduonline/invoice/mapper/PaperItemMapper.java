package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.PaperItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.time.Instant;

@Mapper
public interface PaperItemMapper extends BaseMapper<PaperItem> {
    record PaperContext(String invoiceId, String organizationId, String projectId,
                        String applicantCasId, boolean paperRequired, String invoiceStatus) {
    }

    @Select("""
            SELECT i.id invoice_id,i.organization_id,p.id project_id,a.applicant_cas_id,
              p.paper_required,i.status invoice_status
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE i.organization_id=#{organizationId} AND i.id=#{invoiceId}
            """)
    PaperContext findContext(@Param("organizationId") String organizationId,
                             @Param("invoiceId") String invoiceId);

    @Insert("""
            INSERT IGNORE INTO paper_item(invoice_id,organization_id,status,version)
            VALUES(#{invoiceId},#{organizationId},'PENDING_DELIVERY',0)
            """)
    int createPending(@Param("organizationId") String organizationId,
                      @Param("invoiceId") String invoiceId);

    @Update("""
            UPDATE paper_item SET status=#{toStatus},member_declared_at=#{declaredAt},
              received_by_cas_id=#{receivedBy},received_at=#{receivedAt},note=#{note},version=version+1
            WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}
              AND status=#{fromStatus} AND version=#{version}
            """)
    int transition(@Param("organizationId") String organizationId,
                   @Param("invoiceId") String invoiceId,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("declaredAt") Instant declaredAt,
                   @Param("receivedBy") String receivedBy,
                   @Param("receivedAt") Instant receivedAt,
                   @Param("note") String note,
                   @Param("version") long version);

    @Select("""
            <script>
            SELECT i.id FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            WHERE i.organization_id=#{organizationId} AND af.project_id=#{projectId}
              AND i.status='INTERNALLY_APPROVED'
              <choose>
                <when test="digitalInvoiceNo != null">AND i.digital_invoice_no=#{digitalInvoiceNo}</when>
                <otherwise>AND i.invoice_code=#{invoiceCode} AND i.invoice_number=#{invoiceNumber}</otherwise>
              </choose>
            ORDER BY i.created_at LIMIT 2
            </script>
            """)
    java.util.List<String> findInvoiceForScan(@Param("organizationId") String organizationId,
                                               @Param("projectId") String projectId,
                                               @Param("digitalInvoiceNo") String digitalInvoiceNo,
                                               @Param("invoiceCode") String invoiceCode,
                                               @Param("invoiceNumber") String invoiceNumber);

    @Select("""
            <script>
            SELECT pi.* FROM paper_item pi
            JOIN invoice i ON i.id=pi.invoice_id AND i.organization_id=pi.organization_id
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            WHERE pi.organization_id=#{organizationId} AND af.project_id=#{projectId}
              <if test="status != null">AND pi.status=#{status}</if>
            ORDER BY pi.updated_at DESC,pi.invoice_id DESC
            </script>
            """)
    IPage<PaperItem> findForProject(Page<?> page,
                                    @Param("organizationId") String organizationId,
                                    @Param("projectId") String projectId,
                                    @Param("status") String status);
}
