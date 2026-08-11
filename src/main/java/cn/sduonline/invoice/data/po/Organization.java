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
 * 社团持久化对象。
 */
@TableName("organization")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String name;
    private String type;
    private String status;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
