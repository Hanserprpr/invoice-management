package cn.sduonline.invoice.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
public class NotificationDeliveryConfiguration {
    @Bean
    NotificationDeliveryAdapter notificationDeliveryAdapter(NotificationDeliveryProperties properties,
                                                              tools.jackson.databind.ObjectMapper mapper) {
        if (!properties.isEnabled()) {
            return notification -> new NotificationDeliveryAdapter.DeliveryResult("IN_APP_ONLY");
        }
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getTimeout() == null || properties.getTimeout().isNegative()
                || properties.getTimeout().isZero()) {
            throw new IllegalStateException("通知投递已启用，但端点、密钥或超时配置无效");
        }
        return new HttpNotificationDeliveryAdapter(properties, mapper);
    }
}
