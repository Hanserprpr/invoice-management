package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record InvoiceFileRevisionVO(String id, String fileId, int revisionNo,
                                    String replacementReason, Instant createdAt) {
}
