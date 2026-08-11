package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.dto.LedgerDtos.LedgerFilter;

import java.time.Instant;

public record SavedInvoiceFilterVO(String id, String name, LedgerFilter filter,
                                   boolean isDefault, long version,
                                   Instant createdAt, Instant updatedAt) {
}
