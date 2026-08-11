package cn.sduonline.invoice.storage;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStorageConfigurationTests {
    private final ObjectStorageConfiguration configuration = new ObjectStorageConfiguration();

    @Test
    void disabledStorageStartsWithoutCredentialsAndFailsOnlyWhenUsed() {
        ObjectStorage storage = configuration.objectStorage(new R2StorageProperties());

        assertThatThrownBy(() -> storage.createUploadGrant("key", "application/pdf", 1, "a".repeat(64)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getBizCode())
                                .isEqualTo(BizCode.OBJECT_STORAGE_NOT_CONFIGURED));
    }

    @Test
    void enabledStorageRejectsIncompleteConfiguration() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> configuration.objectStorage(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_ACCOUNT_ID");
    }

    @Test
    void configuredStorageCreatesScopedShortLivedPresignedUrlsWithoutNetworkAccess() throws Exception {
        R2StorageProperties properties = configuredProperties();
        ObjectStorage storage = configuration.objectStorage(properties);
        try {
            ObjectStorage.UploadGrant upload = storage.createUploadGrant(
                    "org/2026/08/12/file", "application/pdf", 128, "a".repeat(64));
            ObjectStorage.DownloadGrant download = storage.createDownloadGrant(
                    "org/2026/08/12/file", "报销凭证.pdf", "application/pdf");

            assertThat(upload.url())
                    .startsWith("https://example-account.r2.cloudflarestorage.com/test-bucket/")
                    .contains("file.upload")
                    .contains("X-Amz-Signature=")
                    .doesNotContain("example-secret");
            assertThat(upload.requiredHeaders()).containsKeys("Content-Type", "x-amz-meta-sha256");
            assertThat(upload.expiresAt()).isBeforeOrEqualTo(java.time.Instant.now().plusSeconds(601));
            assertThat(download.url()).contains("X-Amz-Signature=").contains("response-content-disposition");
        } finally {
            ((AutoCloseable) storage).close();
        }
    }

    private R2StorageProperties configuredProperties() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setEnabled(true);
        properties.setAccountId("example-account");
        properties.setAccessKeyId("example-access");
        properties.setSecretAccessKey("example-secret");
        properties.setBucket("test-bucket");
        properties.setUploadUrlTtl(Duration.ofMinutes(10));
        properties.setDownloadUrlTtl(Duration.ofMinutes(5));
        return properties;
    }
}
