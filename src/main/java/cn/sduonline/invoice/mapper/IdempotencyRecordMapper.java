package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.IdempotencyRecord;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface IdempotencyRecordMapper {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO idempotency_record(
              id,organization_id,scope,cas_id,idempotency_key,request_method,
              request_path,request_hash,status,created_at,expires_at)
            VALUES (#{id},#{organizationId},#{scope},#{casId},#{idempotencyKey},
              #{requestMethod},#{requestPath},#{requestHash},#{status},#{createdAt},#{expiresAt})
            """)
    int insertClaim(IdempotencyRecord record);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM idempotency_record
            WHERE scope=#{scope} AND cas_id=#{casId} AND idempotency_key=#{key}
            FOR UPDATE
            """)
    IdempotencyRecord lockByKey(@Param("scope") String scope,
                                @Param("casId") String casId,
                                @Param("key") String key);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE idempotency_record
            SET response_status=#{responseStatus},response_body=#{responseBody},status='COMPLETED'
            WHERE id=#{id} AND status='PROCESSING'
            """)
    int complete(@Param("id") String id,
                 @Param("responseStatus") int responseStatus,
                 @Param("responseBody") String responseBody);

    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM idempotency_record WHERE id=#{id} AND status='PROCESSING'")
    int release(@Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM idempotency_record WHERE id=#{id} AND expires_at<=#{now}")
    int deleteExpiredClaim(@Param("id") String id, @Param("now") Instant now);

    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            DELETE FROM idempotency_record
            WHERE expires_at<=#{now}
            ORDER BY expires_at,id
            LIMIT #{limit}
            """)
    int deleteExpired(@Param("now") Instant now, @Param("limit") int limit);
}
