package cn.sduonline.invoice.data.vo;

import java.time.LocalDate;
import java.util.List;

public record MemberVO(
        String id,
        String organizationId,
        String casId,
        String name,
        String status,
        LocalDate termStart,
        LocalDate termEnd,
        long version,
        List<String> roles) {
}
