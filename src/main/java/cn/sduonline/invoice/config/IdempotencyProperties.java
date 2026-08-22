package cn.sduonline.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.idempotency")
public class IdempotencyProperties {
    private Duration ttl = Duration.ofMinutes(5);
    private Duration processingTtl = Duration.ofMinutes(5);
    private Duration requestTimeout = Duration.ofSeconds(60);
    private int cleanupBatchSize = 1000;
    private long maxRequestBytes = 2 * 1024 * 1024;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getProcessingTtl() {
        return processingTtl;
    }

    public void setProcessingTtl(Duration processingTtl) {
        this.processingTtl = processingTtl;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }
}
