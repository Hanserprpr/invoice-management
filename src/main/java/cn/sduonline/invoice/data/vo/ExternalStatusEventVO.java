package cn.sduonline.invoice.data.vo;

import java.time.Instant;
import java.util.List;

public record ExternalStatusEventVO(String id, String batchId, String actorCasId,
                                    String eventType, String status, String correctionOfEventId,
                                    String comment, List<String> attachmentFileIds, Instant createdAt) {
}
