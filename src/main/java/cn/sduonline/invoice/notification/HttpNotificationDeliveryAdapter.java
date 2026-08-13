package cn.sduonline.invoice.notification;

import cn.sduonline.invoice.data.po.Notification;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

final class HttpNotificationDeliveryAdapter implements NotificationDeliveryAdapter {
    private final NotificationDeliveryProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    HttpNotificationDeliveryAdapter(NotificationDeliveryProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "notificationId", notification.getId(),
                    "recipientCasId", notification.getRecipientCasId(),
                    "type", notification.getNotificationType(),
                    "title", notification.getTitle(),
                    "content", notification.getContent(),
                    "targetType", notification.getTargetType(),
                    "targetId", notification.getTargetId()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(properties.getTimeout()).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) unavailable();
            return new DeliveryResult("DELIVERED");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BizCode.THIRD_PARTY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
