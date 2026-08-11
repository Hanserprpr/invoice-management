package cn.sduonline.invoice.data.vo;

import java.util.List;
import java.util.Set;

public record ReviewInvoiceDetailVO(ReviewQueueItemVO summary, InvoiceVO invoice,
                                    String internalNote, Set<String> tagItemIds,
                                    List<ClubReviewVO> reviews) {
}
