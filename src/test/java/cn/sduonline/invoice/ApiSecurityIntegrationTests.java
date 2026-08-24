package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.service.ProjectService;
import cn.sduonline.invoice.tenant.TenantContext;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ApiSecurityIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired ProjectService projectService;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20000));
    }

    @Test
    void csrfEndpointReturnsTokenForAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/auth/csrf").with(user("csrf-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(result -> assertThat(result.getResponse()
                        .getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("None"));
    }

    @Test
    void healthProbeIsAnonymousAndEveryResponseHasSafeRequestId() throws Exception {
        mockMvc.perform(get("/api/actuator/health/liveness")
                        .header("X-Request-Id", "d5-health-check"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Request-Id", "d5-health-check"));

        mockMvc.perform(get("/api/auth/me").header("X-Request-Id", "unsafe request id\n"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("X-Request-Id"));
    }

    @Test
    void apiDocsAreAnonymousWhenEnabledAndDescribeBusinessEndpoints() throws Exception {
        mockMvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/csrf'].get").exists())
                .andExpect(jsonPath("$.paths['/api/dictionaries/{dictionaryCode}/items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/organizations/{organizationId}/members/{casId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/platform/organizations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/platform/organizations/{organizationId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reviews/stats'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ApplicationVO.properties.answers.type")
                        .value("object"))
                .andExpect(jsonPath("$.components.schemas.ApplicationVO.properties.answers.properties")
                        .doesNotExist());

        mockMvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void frameworkRoutingErrorsUseStableBusinessCodes() throws Exception {
        mockMvc.perform(get("/api/auth/does-not-exist").with(user("routing-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(post("/api/auth/login-url").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(10002));
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

    @Test
    @Transactional
    void clubAdminCreatesAndReadsProjectThroughHttpApi() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('api-platform','平台管理员','ACTIVE',TRUE)
                """);
        var organization = organizationService.create("api-platform",
                new CreateOrganizationRequest("项目接口测试社团", "CLUB",
                        new InitialAdmin("api-project-admin", "项目接口管理员", null, null)));

        mockMvc.perform(post("/api/projects")
                        .header("X-Organization-Id", organization.id())
                        .with(user("api-project-admin")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"接口创建项目",
                                  "paperRequired":false,
                                  "visibility":"ALL",
                                  "managerCasIds":["api-project-admin"],
                                  "accessGrants":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/projects")
                        .header("X-Organization-Id", organization.id())
                        .with(user("api-project-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("接口创建项目"));
    }

    @Test
    @Transactional
    void projectManagerCreatesFormDraftThroughHttpApi() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('form-api-platform','平台管理员','ACTIVE',TRUE)
                """);
        var organization = organizationService.create("form-api-platform",
                new CreateOrganizationRequest("表单接口测试社团", "CLUB",
                        new InitialAdmin("form-api-admin", "表单接口管理员", null, null)));
        String projectId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "form-api-admin")) {
            projectId = projectService.create("form-api-admin", new CreateProjectRequest(
                    "表单接口项目", null, null, null, false, "ALL", null, null,
                    List.of("form-api-admin"), List.of())).id();
        }

        mockMvc.perform(post("/api/projects/{projectId}/forms", projectId)
                        .header("X-Organization-Id", organization.id())
                        .with(user("form-api-admin")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"接口申请表",
                                  "submissionScope":"ALL_MEMBERS",
                                  "maxSubmissionsPerUser":1,
                                  "schema":{"fields":[{
                                    "key":"purpose",
                                    "type":"TEXT",
                                    "label":"用途",
                                    "required":true
                                  }]}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.draftSchema.fields[0].key").value("purpose"));
    }
}
