package cn.sduonline.invoice.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("asyncJobs")
public class AsyncJobHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;

    public AsyncJobHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        Long stale = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM async_job WHERE status='RUNNING'
                  AND started_at < CURRENT_TIMESTAMP(3) - INTERVAL 20 MINUTE
                """, Long.class);
        Long failed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM async_job WHERE status='FAILED'
                  AND finished_at >= CURRENT_TIMESTAMP(3) - INTERVAL 1 HOUR
                """, Long.class);
        Health.Builder result = stale != null && stale > 0 ? Health.down() : Health.up();
        return result.withDetail("staleRunning", stale == null ? 0 : stale)
                .withDetail("failedLastHour", failed == null ? 0 : failed).build();
    }
}
