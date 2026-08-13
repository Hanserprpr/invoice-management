package cn.sduonline.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit")
public class RateLimitProperties {
    private boolean enabled;
    private int requests = 60;
    private Duration window = Duration.ofMinutes(1);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRequests() { return requests; }
    public void setRequests(int requests) { this.requests = requests; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
}
