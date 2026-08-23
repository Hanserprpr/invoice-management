package cn.sduonline.invoice.data.vo;

import java.time.Instant;

public record OrganizationVO(String id, String name, String type, String status, long version,
                             Instant createdAt, Instant updatedAt) {
}
