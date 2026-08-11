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
 * 字典版本持久化对象。
 */
@TableName("dictionary_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryVersion {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String dictionaryType;
    private Integer versionNo;
    private String status;
    private String publishedByCasId;
    private Instant publishedAt;
    private Instant createdAt;
}
