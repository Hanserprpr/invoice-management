package cn.sduonline.invoice.data.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FileUploadVO(
        FileObjectVO file,
        String method,
        String uploadUrl,
        Map<String, List<String>> requiredHeaders,
        Instant expiresAt) {
}
