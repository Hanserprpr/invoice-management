package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.AsyncJob;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AsyncJobMapper extends BaseMapper<AsyncJob> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM async_job
            WHERE job_type=#{jobType} AND status='PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP(3))
            ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    AsyncJob lockNextPending(@Param("jobType") String jobType);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE async_job SET status='RUNNING',progress=1,
              attempt_count=attempt_count+1,started_at=CURRENT_TIMESTAMP(3),
              finished_at=NULL,next_attempt_at=NULL,error_code=NULL,error_message=NULL
            WHERE id=#{id} AND status='PENDING'
            """)
    int claim(@Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE async_job SET status='SUCCEEDED',progress=100,result_json=#{resultJson},
              error_code=NULL,error_message=NULL,next_attempt_at=NULL,
              finished_at=CURRENT_TIMESTAMP(3)
            WHERE id=#{id} AND status='RUNNING'
            """)
    int succeed(@Param("id") String id, @Param("resultJson") String resultJson);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE async_job SET status='PENDING',progress=0,error_code=#{errorCode},
              error_message=#{errorMessage},next_attempt_at=#{nextAttemptAt}
            WHERE id=#{id} AND status='RUNNING'
            """)
    int retry(@Param("id") String id, @Param("errorCode") String errorCode,
              @Param("errorMessage") String errorMessage,
              @Param("nextAttemptAt") Instant nextAttemptAt);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE async_job SET status='FAILED',progress=100,error_code=#{errorCode},
              error_message=#{errorMessage},next_attempt_at=NULL,
              finished_at=CURRENT_TIMESTAMP(3)
            WHERE id=#{id} AND status='RUNNING'
            """)
    int fail(@Param("id") String id, @Param("errorCode") String errorCode,
             @Param("errorMessage") String errorMessage);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM async_job
            WHERE job_type=#{jobType} AND status='RUNNING' AND started_at<=#{staleBefore}
            ORDER BY started_at,id LIMIT #{limit}
            """)
    List<AsyncJob> findStaleRunning(@Param("jobType") String jobType,
                                    @Param("staleBefore") Instant staleBefore,
                                    @Param("limit") int limit);

    @Select("""
            SELECT * FROM async_job WHERE organization_id=#{organizationId}
              AND job_type=#{jobType} AND target_type=#{targetType} AND target_id=#{targetId}
              AND status IN ('PENDING','RUNNING') ORDER BY created_at DESC LIMIT 1
            """)
    AsyncJob findActiveForTarget(@Param("organizationId") String organizationId,
                                 @Param("jobType") String jobType,
                                 @Param("targetType") String targetType,
                                 @Param("targetId") String targetId);
}
