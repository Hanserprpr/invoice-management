package cn.sduonline.invoice.data.vo;

public record ReviewStatsVO(long submitted, long inReview, long internallyApproved,
                            long reviewedToday, long precheckIssueCount) {
}
