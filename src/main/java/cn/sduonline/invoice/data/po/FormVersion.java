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
 * 申请表版本持久化对象。
 */
@TableName("form_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormVersion {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String formId;
    private Integer versionNo;
    private String schemaJson;
    private String dictionarySnapshotJson;
    private String publishedByCasId;
    private Instant publishedAt;
}
