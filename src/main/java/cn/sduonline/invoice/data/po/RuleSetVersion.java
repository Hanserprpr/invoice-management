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
 * 规则集版本持久化对象。
 */
@TableName("rule_set_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetVersion {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String ruleSetId;
    private Integer versionNo;
    private String rulesJson;
    private Instant effectiveAt;
    private String publishedByCasId;
    private Instant publishedAt;
}
