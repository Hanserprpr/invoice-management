package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典项持久化对象。
 */
@TableName("dictionary_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryItem {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String dictionaryVersionId;
    private String code;
    private String displayName;
    private Integer sortOrder;
    private Boolean enabled;
    private String metadataJson;
}
