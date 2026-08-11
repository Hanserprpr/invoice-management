package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 用户持久化对象。
 */
@TableName("user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @TableId(value = "cas_id", type = IdType.INPUT)
    private String casId;
    private String name;
    private String passwordHash;
    private String status;
    private Boolean isPlatformAdmin;
    private Instant lastLoginAt;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
