package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.FileObject;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

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
}
