package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    @Select("""
            <script>
            SELECT * FROM audit_log WHERE organization_id=#{organizationId}
              <if test="actorCasId != null">AND actor_cas_id=#{actorCasId}</if>
              <if test="action != null">AND action=#{action}</if>
              <if test="objectType != null">AND object_type=#{objectType}</if>
              <if test="objectId != null">AND object_id=#{objectId}</if>
            ORDER BY created_at DESC,id DESC
            </script>
            """)
    IPage<AuditLog> findPage(Page<?> page, @Param("organizationId") String organizationId,
                             @Param("actorCasId") String actorCasId,
                             @Param("action") String action,
                             @Param("objectType") String objectType,
                             @Param("objectId") String objectId);
}
