package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限持久化对象。
 */
@TableName("permission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
}
