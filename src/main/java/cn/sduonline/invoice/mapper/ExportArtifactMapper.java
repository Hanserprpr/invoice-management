package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ExportArtifact;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExportArtifactMapper extends BaseMapper<ExportArtifact> {
    @Select("SELECT * FROM export_artifact WHERE organization_id=#{organizationId} AND batch_id=#{batchId} ORDER BY relative_path")
    List<ExportArtifact> findForBatch(@Param("organizationId") String organizationId,
                                      @Param("batchId") String batchId);
}
