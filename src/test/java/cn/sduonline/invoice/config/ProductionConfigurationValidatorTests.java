package cn.sduonline.invoice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTests {
    @Test
    void rejectsMissingOrDevelopmentProductionSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MYSQL_URL", "jdbc:mysql://127.0.0.1/invoice_management_test")
                .withProperty("R2_ENABLED", "false")
                .withProperty("RATE_LIMIT_ENABLED", "false");
        assertThatThrownBy(() -> validator(environment).run(args()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration rejected")
                .hasMessageContaining("SDU_OIDC_CLIENT_SECRET missing")
                .hasMessageContaining("non-test remote database");
    }

    @Test
    void acceptsCompleteNonPlaceholderSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SDU_OIDC_CLIENT_ID", "sdu-client")
                .withProperty("SDU_OIDC_CLIENT_SECRET", "strong-oidc-value")
                .withProperty("R2_ACCOUNT_ID", "account-id")
                .withProperty("R2_ACCESS_KEY_ID", "access-id")
                .withProperty("R2_SECRET_ACCESS_KEY", "strong-r2-value")
                .withProperty("R2_BUCKET", "invoice-private")
                .withProperty("MYSQL_USERNAME", "invoice_app")
                .withProperty("MYSQL_PASSWORD", "strong-db-value")
                .withProperty("MYSQL_URL", "jdbc:mysql://mysql.internal/invoice_management")
                .withProperty("SPRING_DATA_REDIS_URL", "redis://redis.internal:6379")
                .withProperty("R2_ENABLED", "true")
                .withProperty("RATE_LIMIT_ENABLED", "true");
        assertThatCode(() -> validator(environment).run(args())).doesNotThrowAnyException();
    }

    private ProductionConfigurationValidator validator(MockEnvironment environment) {
        return new ProductionConfigurationValidator(environment);
    }

    private DefaultApplicationArguments args() {
        return new DefaultApplicationArguments(new String[0]);
    }
}
