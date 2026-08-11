package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.vo.InvoiceTimelineEventVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvoiceTimelineMapper {
    @Select("""
            SELECT * FROM (
              SELECT r.id,'REVIEW' event_type,r.action,r.reviewer_cas_id actor_cas_id,
                NULL status,COALESCE(r.comment,di.display_name) detail,r.created_at occurred_at
              FROM club_review r LEFT JOIN dictionary_item di ON di.id=r.reason_item_id
              WHERE r.organization_id=#{organizationId} AND r.invoice_id=#{invoiceId}
              UNION ALL
              SELECT pe.id,'PAPER',pe.event_type,pe.actor_cas_id,pe.to_status,pe.reason,pe.created_at
              FROM paper_event pe WHERE pe.organization_id=#{organizationId} AND pe.invoice_id=#{invoiceId}
              UNION ALL
              SELECT se.id,'SCAN',se.context,se.actor_cas_id,se.result,se.result_detail,se.created_at
              FROM scan_event se WHERE se.organization_id=#{organizationId} AND se.invoice_id=#{invoiceId}
              UNION ALL
              SELECT pr.id,'PRECHECK',COALESCE(pr.rule_code,pr.check_type),pr.resolved_by_cas_id,
                pr.result,pr.reason,pr.created_at
              FROM invoice_precheck_result pr
              WHERE pr.organization_id=#{organizationId} AND pr.invoice_id=#{invoiceId}
              UNION ALL
              SELECT fr.id,'FILE','FILE_REVISION',fr.replaced_by_cas_id,
                CAST(fr.revision_no AS CHAR),fr.replacement_reason,fr.created_at
              FROM invoice_file_revision fr
              WHERE fr.organization_id=#{organizationId} AND fr.invoice_id=#{invoiceId}
              UNION ALL
              SELECT al.id,'AUDIT',al.action,al.actor_cas_id,NULL,NULL,al.created_at
              FROM audit_log al WHERE al.organization_id=#{organizationId}
                AND al.object_type='INVOICE' AND al.object_id=#{invoiceId}
              UNION ALL
              SELECT ee.id,'EXTERNAL',ee.event_type,ee.actor_cas_id,ee.status,ee.comment,ee.created_at
              FROM external_status_event ee
              JOIN export_batch_invoice ebi ON ebi.batch_id=ee.batch_id
                AND ebi.organization_id=ee.organization_id
              WHERE ee.organization_id=#{organizationId} AND ebi.invoice_id=#{invoiceId}
            ) events ORDER BY occurred_at,id
            """)
    List<InvoiceTimelineEventVO> findForInvoice(@Param("organizationId") String organizationId,
                                                @Param("invoiceId") String invoiceId);
}
