package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record FileDownloadVO(String downloadUrl, Instant expiresAt) {
}
