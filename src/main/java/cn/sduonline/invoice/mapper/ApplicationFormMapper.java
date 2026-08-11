package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.ApplicationForm;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationFormMapper extends BaseMapper<ApplicationForm> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT f.* FROM application_form f
            JOIN project p ON p.id=f.project_id AND p.organization_id=f.organization_id
            WHERE f.organization_id=#{organizationId}
              AND f.status='PUBLISHED' AND p.status='COLLECTING'
              AND (f.starts_at IS NULL OR f.starts_at <= CURRENT_TIMESTAMP(3))
              AND (f.ends_at IS NULL OR f.ends_at >= CURRENT_TIMESTAMP(3))
            ORDER BY f.updated_at DESC, f.id DESC
            """)
    List<ApplicationForm> findCurrentlyAvailable(@Param("organizationId") String organizationId);
}
