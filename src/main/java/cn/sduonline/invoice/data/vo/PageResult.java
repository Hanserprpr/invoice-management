package cn.sduonline.invoice.data.vo;

import java.util.List;

public record PageResult<T>(List<T> records, long page, long pageSize, long total) {
}
