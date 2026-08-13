package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ExternalStatusEventAttachment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExternalStatusEventAttachmentMapper {
    @Insert("INSERT INTO external_status_event_attachment(event_id,organization_id,file_id) VALUES(#{eventId},#{organizationId},#{fileId})")
    int insert(ExternalStatusEventAttachment attachment);

    @Select("SELECT file_id FROM external_status_event_attachment WHERE organization_id=#{organizationId} AND event_id=#{eventId} ORDER BY file_id")
    List<String> findFileIds(@Param("organizationId") String organizationId,
                             @Param("eventId") String eventId);
}
