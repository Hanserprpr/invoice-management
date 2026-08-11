package cn.sduonline.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ApiSecurityIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20000));
    }

    @Test
    @Transactional
    void organizationListIsUserScopedAndTenantPathMustMatchHeader() throws Exception {
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES ('api-user','接口用户','ACTIVE',FALSE)");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                "01KAP000000000000000000001", "接口社团甲", "CLUB", "ACTIVE");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                "01KAP000000000000000000002", "接口社团乙", "CLUB", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO organization_member(id,organization_id,cas_id,status)
                VALUES (?,?,?,'ACTIVE'),(?,?,?,'ACTIVE')
                """, "01KAP000000000000000000011", "01KAP000000000000000000001", "api-user",
                "01KAP000000000000000000012", "01KAP000000000000000000002", "api-user");

        mockMvc.perform(get("/api/organizations").with(user("api-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/organizations/01KAP000000000000000000001")
                        .header("X-Organization-Id", "01KAP000000000000000000002")
                        .with(user("api-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20004));
    }
}
