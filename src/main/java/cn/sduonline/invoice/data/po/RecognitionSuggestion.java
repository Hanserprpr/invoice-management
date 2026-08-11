package cn.sduonline.invoice.data.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 识别建议持久化对象。
 */
@TableName("recognition_suggestion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionSuggestion {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String invoiceId;
    private String sourceJobId;
    private String fieldPath;
    private String suggestedValue;
    private BigDecimal confidence;
    private String status;
    private String finalValue;
    private String confirmedByCasId;
    private Instant confirmedAt;
    private Instant createdAt;
}
