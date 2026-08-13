package cn.sduonline.invoice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.worker-enabled", havingValue = "true")
public class NotificationDeliveryScheduler {
    private final NotificationDeliveryWorker worker;
    public NotificationDeliveryScheduler(NotificationDeliveryWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${app.notification.poll-interval:5s}")
    public void process() { while (worker.processNext()) { } }
}
