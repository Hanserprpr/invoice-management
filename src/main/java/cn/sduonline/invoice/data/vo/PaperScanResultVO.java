package cn.sduonline.invoice.data.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaperScanResultVO(String scanEventId,
                                @Schema(allowableValues = {"RECEIVED", "UNSUPPORTED", "NOT_FOUND",
                                        "POSSIBLE_DUPLICATE", "ALREADY_SCANNED", "DATA_MISMATCH"})
                                String result, String invoiceId,
                                PaperItemVO paperItem) {
}
