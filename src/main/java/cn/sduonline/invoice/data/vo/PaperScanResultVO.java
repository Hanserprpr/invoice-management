package cn.sduonline.invoice.data.vo;

public record PaperScanResultVO(String scanEventId, String result, String invoiceId,
                                PaperItemVO paperItem) {
}
