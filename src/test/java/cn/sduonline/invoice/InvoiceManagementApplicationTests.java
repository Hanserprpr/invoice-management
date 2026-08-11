package cn.sduonline.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret",
        "spring.flyway.enabled=false"
})
class InvoiceManagementApplicationTests {

    @Test
    void contextLoads() {
    }

}
