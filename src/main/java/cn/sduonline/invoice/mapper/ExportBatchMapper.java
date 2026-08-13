package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ExportBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.time.Instant;

@Mapper
public interface ExportBatchMapper extends BaseMapper<ExportBatch> {
    @Select("""
            <script>
            SELECT b.* FROM export_batch b WHERE b.organization_id=#{organizationId}
              <if test="projectId != null">AND b.project_id=#{projectId}</if>
              <if test="status != null">AND b.status=#{status}</if>
              AND (#{unrestricted}=TRUE
                OR EXISTS(SELECT 1 FROM project_manager pm
                  JOIN organization_member m ON m.id=pm.member_id AND m.organization_id=pm.organization_id
                  WHERE pm.organization_id=b.organization_id AND pm.project_id=b.project_id
                    AND m.cas_id=#{actorCasId} AND m.status='ACTIVE'
                    AND (m.term_start IS NULL OR m.term_start&lt;=CURRENT_DATE)
                    AND (m.term_end IS NULL OR m.term_end&gt;=CURRENT_DATE))
                OR EXISTS(SELECT 1 FROM project_access pa
                  JOIN organization_member m ON m.id=pa.member_id AND m.organization_id=pa.organization_id
                  WHERE pa.organization_id=b.organization_id AND pa.project_id=b.project_id
                    AND m.cas_id=#{actorCasId} AND m.status='ACTIVE'
                    AND pa.access_type IN ('REVIEW','MANAGE')
                    AND (m.term_start IS NULL OR m.term_start&lt;=CURRENT_DATE)
                    AND (m.term_end IS NULL OR m.term_end&gt;=CURRENT_DATE)))
            ORDER BY b.created_at DESC,b.id DESC
            </script>
            """)
    IPage<ExportBatch> findPage(Page<?> page, @Param("organizationId") String organizationId,
                                @Param("actorCasId") String actorCasId,
                                @Param("unrestricted") boolean unrestricted,
                                @Param("projectId") String projectId,
                                @Param("status") String status);

    @Select("SELECT * FROM export_batch WHERE organization_id=#{organizationId} AND id=#{id}")
    ExportBatch findOne(@Param("organizationId") String organizationId, @Param("id") String id);

    @Select("SELECT * FROM export_batch WHERE organization_id=#{organizationId} AND id=#{id} FOR UPDATE")
    ExportBatch lockOne(@Param("organizationId") String organizationId, @Param("id") String id);

    @Select("SELECT COALESCE(MAX(revision_no),0) FROM export_batch WHERE organization_id=#{organizationId} AND project_id=#{projectId} AND batch_no=#{batchNo}")
    int maxRevision(@Param("organizationId") String organizationId,
                    @Param("projectId") String projectId, @Param("batchNo") String batchNo);

    @Update("""
            UPDATE export_batch SET status='GENERATED',generated_at=#{now},version=version+1
            WHERE organization_id=#{organizationId} AND id=#{id} AND status='DRAFT'
            """)
    int markGenerated(@Param("organizationId") String organizationId,
                      @Param("id") String id, @Param("now") Instant now);

    @Update("""
            UPDATE export_batch SET status='EXPORTED',
              first_downloaded_at=COALESCE(first_downloaded_at,#{now}),version=version+1
            WHERE organization_id=#{organizationId} AND id=#{id}
              AND status IN ('GENERATED','EXPORTED')
            """)
    int markDownloaded(@Param("organizationId") String organizationId,
                       @Param("id") String id, @Param("now") Instant now);
}
