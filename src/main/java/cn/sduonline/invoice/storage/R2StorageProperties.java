package cn.sduonline.invoice.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.storage.r2")
public class R2StorageProperties {
    private boolean enabled;
    private String accountId = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String bucket = "";
    private Duration uploadUrlTtl = Duration.ofMinutes(10);
    private Duration downloadUrlTtl = Duration.ofMinutes(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration attemptTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public Duration getDownloadUrlTtl() { return downloadUrlTtl; }
    public void setDownloadUrlTtl(Duration downloadUrlTtl) { this.downloadUrlTtl = downloadUrlTtl; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Duration getAttemptTimeout() { return attemptTimeout; }
    public void setAttemptTimeout(Duration attemptTimeout) { this.attemptTimeout = attemptTimeout; }
}
