package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Invoice;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecognitionInvoiceMapper {

    @Select("""
            SELECT i.* FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            WHERE i.organization_id=#{organizationId} AND i.id=#{invoiceId}
              AND a.applicant_cas_id=#{actorCasId}
            """)
    Invoice findOwnedById(@Param("organizationId") String organizationId,
                          @Param("invoiceId") String invoiceId,
                          @Param("actorCasId") String actorCasId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM invoice WHERE organization_id=#{organizationId} AND id=#{invoiceId}
            FOR UPDATE
            """)
    Invoice lockById(@Param("organizationId") String organizationId,
                     @Param("invoiceId") String invoiceId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE invoice SET status='PENDING_RECOGNITION',version=version+1
            WHERE organization_id=#{organizationId} AND id=#{invoiceId} AND status='DRAFT'
            """)
    int markPendingIfDraft(@Param("organizationId") String organizationId,
                           @Param("invoiceId") String invoiceId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE invoice SET status='DRAFT',version=version+1
            WHERE organization_id=#{organizationId} AND id=#{invoiceId}
              AND status='PENDING_RECOGNITION'
            """)
    int restoreDraftIfPending(@Param("organizationId") String organizationId,
                              @Param("invoiceId") String invoiceId);
}
