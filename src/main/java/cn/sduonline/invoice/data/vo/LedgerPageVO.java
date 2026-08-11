package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.util.List;

public record LedgerPageVO(List<LedgerInvoiceVO> records, long page, long pageSize, long total,
                           BigDecimal totalFaceAmount, BigDecimal totalClaimedAmount) {
}
