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
 * 已保存的发票筛选持久化对象。
 */
@TableName("saved_invoice_filter")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedInvoiceFilter {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String ownerCasId;
    private String organizationId;
    private String name;
    private String filterJson;
    private Boolean isDefault;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
