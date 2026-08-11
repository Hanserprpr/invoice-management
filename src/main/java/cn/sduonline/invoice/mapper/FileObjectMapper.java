package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.FileObject;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_object WHERE organization_id=#{organizationId} AND id=#{fileId}")
    FileObject findByOrganizationAndId(@Param("organizationId") String organizationId,
                                       @Param("fileId") String fileId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE file_object SET scan_status=#{status}, preview_file_id=#{previewFileId},
              ready_at=#{readyAt}, expires_at=#{expiresAt}
            WHERE organization_id=#{organizationId} AND id=#{fileId}
            """)
    int updateInspection(@Param("organizationId") String organizationId,
                         @Param("fileId") String fileId,
                         @Param("status") String status,
                         @Param("previewFileId") String previewFileId,
                         @Param("readyAt") Instant readyAt,
                         @Param("expiresAt") Instant expiresAt);

    @Select("""
            SELECT (EXISTS(SELECT 1 FROM invoice WHERE organization_id=#{organizationId}
                    AND current_file_id=#{fileId})
              OR EXISTS(SELECT 1 FROM invoice_file_revision WHERE organization_id=#{organizationId}
                    AND file_id=#{fileId})
              OR EXISTS(SELECT 1 FROM attachment WHERE organization_id=#{organizationId}
                    AND file_id=#{fileId} AND status='ACTIVE'))
            """)
    boolean isReferenced(@Param("organizationId") String organizationId,
                         @Param("fileId") String fileId);

    @Update("UPDATE file_object SET expires_at=NULL WHERE organization_id=#{organizationId} AND id=#{fileId}")
    int markReferenced(@Param("organizationId") String organizationId,
                       @Param("fileId") String fileId);

    @Update("""
            UPDATE file_object SET scan_status='SCANNING'
            WHERE organization_id=#{organizationId} AND id=#{fileId} AND scan_status='PENDING'
            """)
    int markUploadCompleted(@Param("organizationId") String organizationId,
                            @Param("fileId") String fileId);

    @Select("""
            SELECT DISTINCT linked.invoice_id FROM (
              SELECT i.id invoice_id FROM invoice i
                WHERE i.organization_id=#{organizationId} AND i.current_file_id=#{fileId}
              UNION ALL
              SELECT r.invoice_id FROM invoice_file_revision r
                WHERE r.organization_id=#{organizationId} AND r.file_id=#{fileId}
              UNION ALL
              SELECT a.invoice_id FROM attachment a
                WHERE a.organization_id=#{organizationId} AND a.file_id=#{fileId} AND a.status='ACTIVE'
              UNION ALL
              SELECT i.id FROM invoice i JOIN file_object f ON f.id=i.current_file_id
                WHERE i.organization_id=#{organizationId} AND f.preview_file_id=#{fileId}
              UNION ALL
              SELECT r.invoice_id FROM invoice_file_revision r
                JOIN file_object f ON f.id=r.file_id
                WHERE r.organization_id=#{organizationId} AND f.preview_file_id=#{fileId}
              UNION ALL
              SELECT a.invoice_id FROM attachment a JOIN file_object f ON f.id=a.file_id
                WHERE a.organization_id=#{organizationId} AND a.status='ACTIVE'
                  AND f.preview_file_id=#{fileId}
            ) linked
            """)
    List<String> findLinkedInvoiceIds(@Param("organizationId") String organizationId,
                                      @Param("fileId") String fileId);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE file_object SET scan_status=#{status},ready_at=#{readyAt},expires_at=#{expiresAt}
            WHERE organization_id=#{organizationId} AND id=#{fileId} AND scan_status='SCANNING'
            """)
    int updateSecurityScanResult(@Param("organizationId") String organizationId,
                                 @Param("fileId") String fileId,
                                 @Param("status") String status,
                                 @Param("readyAt") Instant readyAt,
                                 @Param("expiresAt") Instant expiresAt);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT f.* FROM file_object f
            WHERE f.scan_status IN ('PENDING','REJECTED','FAILED')
              AND f.expires_at IS NOT NULL AND f.expires_at<=CURRENT_TIMESTAMP(3)
              AND NOT EXISTS(SELECT 1 FROM file_object p WHERE p.preview_file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM invoice i WHERE i.current_file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM invoice_file_revision r WHERE r.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM attachment a WHERE a.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM export_artifact e WHERE e.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM external_status_event_attachment x WHERE x.file_id=f.id)
            ORDER BY f.expires_at,f.id LIMIT #{limit}
            """)
    List<FileObject> findExpiredUnreferenced(@Param("limit") int limit);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT f.* FROM file_object f
            WHERE f.organization_id=#{organizationId} AND f.id=#{fileId}
              AND f.scan_status IN ('PENDING','REJECTED','FAILED')
              AND f.expires_at IS NOT NULL AND f.expires_at<=CURRENT_TIMESTAMP(3)
              AND NOT EXISTS(SELECT 1 FROM file_object p WHERE p.preview_file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM invoice i WHERE i.current_file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM invoice_file_revision r WHERE r.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM attachment a WHERE a.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM export_artifact e WHERE e.file_id=f.id)
              AND NOT EXISTS(SELECT 1 FROM external_status_event_attachment x WHERE x.file_id=f.id)
            FOR UPDATE
            """)
    FileObject lockExpiredUnreferenced(@Param("organizationId") String organizationId,
                                       @Param("fileId") String fileId);

    @InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Delete("""
            DELETE f FROM file_object f
              LEFT JOIN file_object p ON p.preview_file_id=f.id
              LEFT JOIN invoice i ON i.current_file_id=f.id
              LEFT JOIN invoice_file_revision r ON r.file_id=f.id
              LEFT JOIN attachment a ON a.file_id=f.id
              LEFT JOIN export_artifact e ON e.file_id=f.id
              LEFT JOIN external_status_event_attachment x ON x.file_id=f.id
            WHERE f.organization_id=#{organizationId} AND f.id=#{fileId}
              AND f.scan_status IN ('PENDING','REJECTED','FAILED')
              AND f.expires_at IS NOT NULL AND f.expires_at<=CURRENT_TIMESTAMP(3)
              AND p.id IS NULL AND i.id IS NULL AND r.id IS NULL AND a.id IS NULL
              AND e.id IS NULL AND x.event_id IS NULL
            """)
    int deleteExpiredUnreferenced(@Param("organizationId") String organizationId,
                                  @Param("fileId") String fileId);
}
