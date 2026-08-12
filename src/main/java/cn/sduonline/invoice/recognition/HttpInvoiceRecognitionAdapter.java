package cn.sduonline.invoice.recognition;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

final class HttpInvoiceRecognitionAdapter implements InvoiceRecognitionAdapter {
    private final OcrProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    HttpInvoiceRecognitionAdapter(OcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public RecognitionResult recognize(Path file, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(properties.getTimeout())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofFile(file)).build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            ProviderResponse body = objectMapper.readValue(response.body(), ProviderResponse.class);
            return new RecognitionResult(body.rawText(), body.qrRaw(),
                    body.fields() == null ? Map.of() : body.fields(), true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            if (exception instanceof BusinessException business) throw business;
            throw unavailable();
        }
    }

    record ProviderResponse(String rawText, String qrRaw,
                            Map<String, SuggestedField> fields) {
    }

    private BusinessException unavailable() {
        return new BusinessException(BizCode.OCR_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
