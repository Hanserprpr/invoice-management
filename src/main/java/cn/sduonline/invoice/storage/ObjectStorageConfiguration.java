package cn.sduonline.invoice.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(R2StorageProperties.class)
public class ObjectStorageConfiguration {
    @Bean
    ObjectStorage objectStorage(R2StorageProperties properties) {
        if (!properties.isEnabled()) return new DisabledObjectStorage();
        require(properties.getAccountId(), "R2_ACCOUNT_ID");
        require(properties.getAccessKeyId(), "R2_ACCESS_KEY_ID");
        require(properties.getSecretAccessKey(), "R2_SECRET_ACCESS_KEY");
        require(properties.getBucket(), "R2_BUCKET");
        requireTtl(properties.getUploadUrlTtl(), "R2_UPLOAD_URL_TTL");
        requireTtl(properties.getDownloadUrlTtl(), "R2_DOWNLOAD_URL_TTL");
        return new R2ObjectStorage(properties);
    }

    private void require(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " must be configured when R2_ENABLED=true");
        }
    }

    private void requireTtl(Duration value, String variable) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException(variable + " must be between 1 second and 1 hour");
        }
    }
}
