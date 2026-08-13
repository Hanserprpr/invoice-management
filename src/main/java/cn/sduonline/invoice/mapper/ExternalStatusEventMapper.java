package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ExternalStatusEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExternalStatusEventMapper extends BaseMapper<ExternalStatusEvent> {
    @Select("SELECT * FROM external_status_event WHERE organization_id=#{organizationId} AND batch_id=#{batchId} ORDER BY created_at,id")
    List<ExternalStatusEvent> findForBatch(@Param("organizationId") String organizationId,
                                           @Param("batchId") String batchId);

    @Select("SELECT * FROM external_status_event WHERE organization_id=#{organizationId} AND batch_id=#{batchId} AND id=#{eventId}")
    ExternalStatusEvent findOne(@Param("organizationId") String organizationId,
                                @Param("batchId") String batchId,
                                @Param("eventId") String eventId);
}
