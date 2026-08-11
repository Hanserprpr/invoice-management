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
 * 规则集持久化对象。
 */
@TableName("rule_set")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSet {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String name;
    private Boolean isDefault;
    private String status;
    @Version
    private Long version;
    private String createdByCasId;
    private Instant createdAt;
    private Instant updatedAt;
}
