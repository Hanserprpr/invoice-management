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
 * 文件对象持久化对象。
 */
@TableName("file_object")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileObject {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String organizationId;
    private String uploaderCasId;
    private String storageKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String imageFingerprint;
    private String purpose;
    private String scanStatus;
    private String previewFileId;
    private Instant readyAt;
    private Instant expiresAt;
    private Instant createdAt;
}
