package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.FormVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FormVersionMapper extends BaseMapper<FormVersion> {

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) FROM form_version
            WHERE organization_id=#{organizationId} AND form_id=#{formId}
            """)
    int findMaxVersionNo(@Param("organizationId") String organizationId,
                         @Param("formId") String formId);

    @Select("""
            SELECT * FROM form_version
            WHERE organization_id=#{organizationId} AND form_id=#{formId}
            ORDER BY version_no DESC
            """)
    List<FormVersion> findForForm(@Param("organizationId") String organizationId,
                                  @Param("formId") String formId);

    @Select("""
            SELECT * FROM form_version
            WHERE organization_id=#{organizationId} AND form_id=#{formId} AND version_no=#{versionNo}
            """)
    FormVersion findByVersionNo(@Param("organizationId") String organizationId,
                                @Param("formId") String formId,
                                @Param("versionNo") int versionNo);

    @Select("""
            SELECT * FROM form_version
            WHERE organization_id=#{organizationId} AND form_id=#{formId}
            ORDER BY version_no DESC LIMIT 1
            """)
    FormVersion findLatest(@Param("organizationId") String organizationId,
                           @Param("formId") String formId);
}
