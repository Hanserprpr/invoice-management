package cn.sduonline.invoice.service;

import cn.sduonline.invoice.config.IdempotencyProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class IdempotencyCleanupScheduler {
    private final IdempotencyService service;
    private final IdempotencyProperties properties;

    public IdempotencyCleanupScheduler(IdempotencyService service,
                                       IdempotencyProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval:1h}")
    public void cleanup() {
        service.cleanupExpired(Instant.now(), properties.getCleanupBatchSize());
    }
}
