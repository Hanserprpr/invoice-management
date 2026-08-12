package cn.sduonline.invoice.recognition;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

public interface InvoiceRecognitionAdapter {
    RecognitionResult recognize(Path file, String contentType);

    record RecognitionResult(String rawText, String qrRaw,
                             Map<String, SuggestedField> fields,
                             boolean available) {
    }

    record SuggestedField(String value, BigDecimal confidence) {
    }
}
