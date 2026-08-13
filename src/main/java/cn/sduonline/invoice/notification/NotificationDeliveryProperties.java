package cn.sduonline.invoice.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties("app.notification.delivery")
public class NotificationDeliveryProperties {
    private boolean enabled;
    private String endpoint;
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(10);
}
