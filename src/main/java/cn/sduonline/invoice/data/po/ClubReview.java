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
 * 社团审核记录持久化对象。
 */
@TableName("club_review")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubReview {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String reviewerCasId;
    private String action;
    private String reasonItemId;
    private String returnFieldsJson;
    private String comment;
    private String batchOperationId;
    private Instant createdAt;
}
