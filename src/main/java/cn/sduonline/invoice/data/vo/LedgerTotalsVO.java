package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;

public record LedgerTotalsVO(long total, BigDecimal totalFaceAmount,
                             BigDecimal totalClaimedAmount) {
}
