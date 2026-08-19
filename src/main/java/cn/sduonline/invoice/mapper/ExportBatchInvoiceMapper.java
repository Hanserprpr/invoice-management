package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ExportBatchInvoice;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ExportBatchInvoiceMapper {
    record Candidate(String invoiceId, String projectId, String applicationId,
                     String applicantCasId, String applicantName, String invoiceType,
                     String invoiceCode, String invoiceNumber, String digitalInvoiceNo,
                     LocalDate invoiceDate, String sellerName, String sellerTaxNo,
                     BigDecimal faceAmount, BigDecimal claimedAmount, String currentFileId,
                     String status) {
    }

    record SourceFile(String invoiceId, String fileId, String storageKey,
                      String originalName, String contentType) {
    }

    @Select("""
            <script>
            SELECT i.id invoice_id,p.id project_id,i.application_id,a.applicant_cas_id,
              u.name applicant_name,i.invoice_type,i.invoice_code,i.invoice_number,
              i.digital_invoice_no,i.invoice_date,i.seller_name,i.seller_tax_no,
              i.face_amount,i.claimed_amount,i.current_file_id,i.status
            FROM invoice i
            JOIN application a ON a.id=i.application_id AND a.organization_id=i.organization_id
            JOIN `user` u ON u.cas_id=a.applicant_cas_id
            JOIN form_version fv ON fv.id=a.form_version_id AND fv.organization_id=a.organization_id
            JOIN application_form af ON af.id=fv.form_id AND af.organization_id=fv.organization_id
            JOIN project p ON p.id=af.project_id AND p.organization_id=af.organization_id
            WHERE i.organization_id=#{organizationId} AND i.id IN
              <foreach collection="invoiceIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY i.id FOR UPDATE
            </script>
            """)
    List<Candidate> lockCandidates(@Param("organizationId") String organizationId,
                                   @Param("invoiceIds") List<String> invoiceIds);

    @Insert("""
            INSERT INTO export_batch_invoice(batch_id,organization_id,invoice_id,sequence_no,
              snapshot_face_amount,snapshot_claimed_amount,snapshot_json)
            VALUES(#{batchId},#{organizationId},#{invoiceId},#{sequenceNo},
              #{snapshotFaceAmount},#{snapshotClaimedAmount},#{snapshotJson})
            """)
    int insert(ExportBatchInvoice item);

    @Select("SELECT * FROM export_batch_invoice WHERE organization_id=#{organizationId} AND batch_id=#{batchId} ORDER BY sequence_no")
    List<ExportBatchInvoice> findForBatch(@Param("organizationId") String organizationId,
                                          @Param("batchId") String batchId);

    @Update("""
            <script>
            UPDATE invoice SET status='IN_EXPORT_BATCH',version=version+1
            WHERE organization_id=#{organizationId} AND status='INTERNALLY_APPROVED'
              AND id IN
              <foreach collection="invoiceIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int reserveInvoices(@Param("organizationId") String organizationId,
                        @Param("invoiceIds") List<String> invoiceIds);

    @Select("""
            SELECT DISTINCT i.application_id
            FROM export_batch_invoice e
            JOIN invoice i ON i.id=e.invoice_id AND i.organization_id=e.organization_id
            WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
            ORDER BY i.application_id
            """)
    List<String> findApplicationIdsForBatch(@Param("organizationId") String organizationId,
                                             @Param("batchId") String batchId);

    @Select("""
            SELECT COUNT(*)
            FROM export_batch_invoice e
            JOIN invoice i ON i.id=e.invoice_id AND i.organization_id=e.organization_id
            JOIN invoice_export_reservation r ON r.invoice_id=i.id
              AND r.organization_id=i.organization_id AND r.batch_id=e.batch_id
            WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
              AND i.status='IN_EXPORT_BATCH'
            """)
    int countActiveInvoices(@Param("organizationId") String organizationId,
                            @Param("batchId") String batchId);

    @Update("""
            UPDATE invoice i
            JOIN export_batch_invoice e ON e.invoice_id=i.id AND e.organization_id=i.organization_id
            JOIN invoice_export_reservation r ON r.invoice_id=i.id
              AND r.organization_id=i.organization_id AND r.batch_id=e.batch_id
            SET i.status='INTERNALLY_APPROVED',i.version=i.version+1
            WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
              AND i.status='IN_EXPORT_BATCH'
            """)
    int restoreInvoices(@Param("organizationId") String organizationId,
                        @Param("batchId") String batchId);

    @Update("""
            UPDATE invoice i
            JOIN export_batch_invoice e ON e.invoice_id=i.id AND e.organization_id=i.organization_id
            JOIN invoice_export_reservation r ON r.invoice_id=i.id
              AND r.organization_id=i.organization_id AND r.batch_id=e.batch_id
            SET i.status='ARCHIVED',i.version=i.version+1
            WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
              AND i.status='IN_EXPORT_BATCH'
            """)
    int archiveInvoices(@Param("organizationId") String organizationId,
                        @Param("batchId") String batchId);

    @Select("""
            SELECT files.invoice_id,files.file_id,files.storage_key,files.original_name,files.content_type
            FROM (
              SELECT e.sequence_no,0 file_order,i.id invoice_id,f.id file_id,f.storage_key,
                f.original_name,f.content_type
              FROM export_batch_invoice e
              JOIN invoice i ON i.id=e.invoice_id AND i.organization_id=e.organization_id
              JOIN file_object f ON f.id=i.current_file_id AND f.organization_id=i.organization_id
              WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
              UNION ALL
              SELECT e.sequence_no,1 file_order,i.id invoice_id,f.id file_id,f.storage_key,
                f.original_name,f.content_type
              FROM export_batch_invoice e
              JOIN invoice i ON i.id=e.invoice_id AND i.organization_id=e.organization_id
              JOIN attachment a ON a.invoice_id=i.id AND a.organization_id=i.organization_id
                AND a.status='ACTIVE'
              JOIN file_object f ON f.id=a.file_id AND f.organization_id=a.organization_id
              WHERE e.organization_id=#{organizationId} AND e.batch_id=#{batchId}
            ) files ORDER BY files.sequence_no,files.file_order,files.file_id
            """)
    List<SourceFile> findSourceFiles(@Param("organizationId") String organizationId,
                                     @Param("batchId") String batchId);
}
