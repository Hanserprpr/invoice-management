package cn.sduonline.invoice.data.vo;

import java.time.Instant;
import java.util.List;

public record HandoverRecordVO(String id, String outgoingCasId, String incomingCasId,
                               String performedByCasId, List<String> transferredProjectIds,
                               int transferredAccessCount, String comment, Instant createdAt) {
}
