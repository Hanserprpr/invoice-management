package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 一次性密码设置令牌持久化对象。
 */
@TableName("password_setup_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetupToken {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String casId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private String createdByCasId;
    private Instant createdAt;
}
