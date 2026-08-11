package cn.sduonline.invoice;

import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.dto.OrganizationDtos.ReplaceRolesRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.RoleAssignment;
import cn.sduonline.invoice.tenant.TenantContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class DatabaseMigrationIntegrationTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Flyway flyway;
    @Autowired OrganizationMemberMapper memberMapper;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(database).endsWith("_test");
    }

    @Test
    void migratesAllTablesAndReferenceDataAndIsRepeatable() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(tableCount).isEqualTo(42);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dictionary_version WHERE organization_id IS NULL", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='dictionary_item'
                  AND column_name='organization_id'
                """, String.class)).isEqualTo("YES");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    @Transactional
    void tenantInterceptorAndExplicitOrganizationQueryStayScoped() {
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,FALSE)",
                "test-user", "测试用户", "ACTIVE");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                "01KTEST0000000000000000001", "测试社团甲", "CLUB", "ACTIVE");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                "01KTEST0000000000000000002", "测试社团乙", "CLUB", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO organization_member(id,organization_id,cas_id,status)
                VALUES (?,?,?,'ACTIVE'),(?,?,?,'INACTIVE')
                """, "01KTEST0000000000000000011", "01KTEST0000000000000000001", "test-user",
                "01KTEST0000000000000000012", "01KTEST0000000000000000002", "test-user");

        assertThat(memberMapper.findOrganizationsForUser("test-user"))
                .extracting("id").containsExactly("01KTEST0000000000000000001");
        try (TenantContext.Scope ignored = TenantContext.open(
                "01KTEST0000000000000000001", "test-user")) {
            assertThat(memberMapper.selectList(null)).hasSize(1);
            assertThat(memberMapper.selectList(null).getFirst().getOrganizationId())
                    .isEqualTo("01KTEST0000000000000000001");
        }
    }

    @Test
    @Transactional
    void platformCreatesUsableOrganizationAndClubAdminManagesRoles() {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('platform-test','平台管理员','ACTIVE',TRUE)
                """);
        var organization = organizationService.create("platform-test",
                new CreateOrganizationRequest("服务集成测试社团", "CLUB",
                        new InitialAdmin("club-admin-test", "社团管理员", null, null)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dictionary_version WHERE organization_id=?",
                Integer.class, organization.id())).isEqualTo(2);
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "club-admin-test")) {
            var member = memberService.create(organization.id(), "club-admin-test",
                    new CreateMemberRequest("reviewer-test", "审核人", null, null));
            assertThat(member.roles()).containsExactly("MEMBER");

            var replaced = memberService.replaceRoles(organization.id(), "reviewer-test",
                    "club-admin-test", new ReplaceRolesRequest(member.version(),
                            List.of(new RoleAssignment("REVIEWER", null, null)), List.of()));
            assertThat(replaced.version()).isEqualTo(1);
            assertThat(replaced.roles()).containsExactly("REVIEWER");
        }
    }
}
