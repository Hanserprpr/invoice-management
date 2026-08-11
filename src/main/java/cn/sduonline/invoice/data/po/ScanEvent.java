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
 * 扫码事件持久化对象。
 */
@TableName("scan_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanEvent {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String projectId;
    private String actorCasId;
    private String context;
    private String rawHash;
    private String parsedIdentityJson;
    private String result;
    private String resultDetail;
    private Instant createdAt;
}
