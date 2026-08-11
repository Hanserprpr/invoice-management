package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 项目持久化对象。
 */
@TableName("project")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal budget;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fundingSource;
    private Boolean paperRequired;
    private String visibility;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ruleSetVersionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant startAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant endAt;
    private String status;
    @Version
    private Long version;
    private String createdByCasId;
    private Instant createdAt;
    private Instant updatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant archivedAt;
}
