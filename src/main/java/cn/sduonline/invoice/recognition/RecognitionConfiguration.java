package cn.sduonline.invoice.recognition;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class RecognitionConfiguration {
    @Bean
    InvoiceRecognitionAdapter invoiceRecognitionAdapter(OcrProperties properties,
                                                         ObjectMapper objectMapper) {
        if (!properties.isEnabled()) {
            return (file, contentType) -> new InvoiceRecognitionAdapter.RecognitionResult(
                    null, null, Map.of(), false);
        }
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getTimeout() == null || properties.getTimeout().isZero()
                || properties.getTimeout().isNegative()) {
            throw new IllegalStateException("Invalid OCR configuration");
        }
        return new HttpInvoiceRecognitionAdapter(properties, objectMapper);
    }
}
