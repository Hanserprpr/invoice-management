package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ApplicationRevision;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationRevisionMapper extends BaseMapper<ApplicationRevision> {

    @Select("""
            SELECT COALESCE(MAX(revision_no),0) FROM application_revision
            WHERE organization_id=#{organizationId} AND application_id=#{applicationId}
            """)
    int findMaxRevisionNo(@Param("organizationId") String organizationId,
                          @Param("applicationId") String applicationId);

    @Select("""
            SELECT * FROM application_revision
            WHERE organization_id=#{organizationId} AND application_id=#{applicationId}
            ORDER BY revision_no DESC
            """)
    List<ApplicationRevision> findForApplication(@Param("organizationId") String organizationId,
                                                 @Param("applicationId") String applicationId);
}
