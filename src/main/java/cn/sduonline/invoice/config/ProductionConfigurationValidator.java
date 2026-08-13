package cn.sduonline.invoice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {
    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();
        requireSecret("SDU_OIDC_CLIENT_ID", errors);
        requireSecret("SDU_OIDC_CLIENT_SECRET", errors);
        requireSecret("R2_ACCOUNT_ID", errors);
        requireSecret("R2_ACCESS_KEY_ID", errors);
        requireSecret("R2_SECRET_ACCESS_KEY", errors);
        requireSecret("R2_BUCKET", errors);
        requireSecret("MYSQL_USERNAME", errors);
        requireSecret("MYSQL_PASSWORD", errors);
        requireUrl("MYSQL_URL", List.of("jdbc:mysql://"), errors);
        requireUrl("SPRING_DATA_REDIS_URL", List.of("redis://", "rediss://"), errors);
        requireTrue("R2_ENABLED", errors);
        requireTrue("RATE_LIMIT_ENABLED", errors);
        rejectTestDatabase(errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production configuration rejected: "
                    + String.join(", ", errors));
        }
    }

    private void requireSecret(String name, List<String> errors) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank() || isPlaceholder(value)) errors.add(name + " missing");
    }

    private void requireUrl(String name, List<String> prefixes, List<String> errors) {
        String value = environment.getProperty(name);
        try {
            String parseable = value != null && value.startsWith("jdbc:")
                    ? value.substring(5) : value;
            boolean prefixMatches = value != null && prefixes.stream().anyMatch(value::startsWith);
            if (!prefixMatches || URI.create(parseable).getHost() == null) {
                errors.add(name + " invalid");
            }
        } catch (RuntimeException exception) {
            errors.add(name + " invalid");
        }
    }

    private void requireTrue(String name, List<String> errors) {
        if (!environment.getProperty(name, Boolean.class, false)) errors.add(name + " must be true");
    }

    private void rejectTestDatabase(List<String> errors) {
        String url = environment.getProperty("MYSQL_URL", "").toLowerCase();
        if (url.contains("_test") || url.contains("localhost") || url.contains("127.0.0.1")) {
            errors.add("MYSQL_URL must target a non-test remote database");
        }
    }

    private boolean isPlaceholder(String value) {
        String lower = value.toLowerCase();
        return lower.contains("changeme") || lower.contains("example")
                || lower.equals("password") || lower.equals("secret") || lower.equals("invoice_dev");
    }
}
