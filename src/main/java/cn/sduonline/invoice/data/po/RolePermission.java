package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色权限关联持久化对象。
 */
@TableName("role_permission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    private Long roleId;
    private Long permissionId;
}
