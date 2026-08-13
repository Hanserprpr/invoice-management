package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.time.Instant;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    @Select("""
            <script>
            SELECT * FROM notification WHERE organization_id=#{organizationId}
              AND recipient_cas_id=#{casId}
              <if test="status != null">AND status=#{status}</if>
            ORDER BY created_at DESC,id DESC
            </script>
            """)
    IPage<Notification> findInbox(Page<?> page, @Param("organizationId") String organizationId,
                                  @Param("casId") String casId, @Param("status") String status);

    @Update("""
            UPDATE notification SET status=#{status},read_at=#{readAt}
            WHERE organization_id=#{organizationId} AND recipient_cas_id=#{casId} AND id=#{id}
              AND status IN ('UNREAD','READ')
            """)
    int changeStatus(@Param("organizationId") String organizationId, @Param("casId") String casId,
                     @Param("id") String id, @Param("status") String status,
                     @Param("readAt") Instant readAt);
}
