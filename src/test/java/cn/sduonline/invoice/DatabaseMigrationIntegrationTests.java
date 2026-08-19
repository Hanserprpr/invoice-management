package cn.sduonline.invoice;

import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.service.AsyncJobClaimService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DuplicateKeyException;

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
    @Autowired AsyncJobClaimService asyncJobClaimService;

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
        assertThat(jdbcTemplate.queryForObject("""
                SELECT CONCAT(data_type, ':', is_nullable) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='application_form'
                  AND column_name='draft_schema_json'
                """, String.class)).isEqualTo("json:NO");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT EXTRA FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='async_job'
                  AND column_name='active_slot'
                """, String.class)).isEqualTo("STORED GENERATED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='async_job'
                  AND index_name='uk_async_job_active_target' AND non_unique=0
                """, Integer.class)).isEqualTo(5);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void onlyOneActiveJobMayExistForTheSameTarget() throws Exception {
        String organizationId = "01KACTIVEJOB00000000000001";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES ('active-job-user','Active Job','ACTIVE',FALSE)");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                organizationId, "Active job organization", "CLUB", "ACTIVE");
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> insertActiveJob(
                        "01KACTIVEJOB00000000000011", organizationId, ready, start));
                var second = executor.submit(() -> insertActiveJob(
                        "01KACTIVEJOB00000000000012", organizationId, ready, start));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            }

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM async_job WHERE organization_id=?
                      AND job_type='INVOICE_RECOGNITION' AND target_id='same-target'
                      AND status IN ('PENDING','RUNNING')
                    """, Integer.class, organizationId)).isEqualTo(1);
            jdbcTemplate.update("""
                    UPDATE async_job SET status='SUCCEEDED' WHERE organization_id=?
                      AND job_type='INVOICE_RECOGNITION' AND target_id='same-target'
                    """, organizationId);
            jdbcTemplate.update("""
                    INSERT INTO async_job(id,organization_id,job_type,target_type,target_id,status,
                      progress,attempt_count,max_attempts,created_by_cas_id)
                    VALUES ('01KACTIVEJOB00000000000013',?,'INVOICE_RECOGNITION','INVOICE',
                      'same-target','PENDING',0,0,3,'active-job-user')
                    """, organizationId);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM async_job WHERE organization_id=?
                      AND job_type='INVOICE_RECOGNITION' AND target_id='same-target'
                    """, Integer.class, organizationId)).isEqualTo(2);
        } finally {
            jdbcTemplate.update("DELETE FROM async_job WHERE organization_id=?", organizationId);
            jdbcTemplate.update("DELETE FROM organization WHERE id=?", organizationId);
            jdbcTemplate.update("DELETE FROM `user` WHERE cas_id='active-job-user'");
        }
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

    @Test
    void multipleInstancesClaimDifferentAsyncJobs() throws Exception {
        String organizationId = "01KD5MULTI0000000000000001";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES ('d5-worker','D5 Worker','ACTIVE',FALSE)");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,?,?)",
                organizationId, "D5 multi instance", "CLUB", "ACTIVE");
        jdbcTemplate.update("""
                INSERT INTO async_job(id,organization_id,job_type,target_type,target_id,status,
                  progress,attempt_count,max_attempts,created_by_cas_id)
                VALUES ('01KD5MULTI0000000000000011',?,'D5_MULTI_INSTANCE','TEST','one','PENDING',0,0,3,'d5-worker'),
                       ('01KD5MULTI0000000000000012',?,'D5_MULTI_INSTANCE','TEST','two','PENDING',0,0,3,'d5-worker')
                """, organizationId, organizationId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> asyncJobClaimService.claim("D5_MULTI_INSTANCE"));
            var second = executor.submit(() -> asyncJobClaimService.claim("D5_MULTI_INSTANCE"));
            var firstJob = first.get(10, TimeUnit.SECONDS).orElseThrow();
            var secondJob = second.get(10, TimeUnit.SECONDS).orElseThrow();
            assertThat(firstJob.getId()).isNotEqualTo(secondJob.getId());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM async_job WHERE organization_id=? AND status='RUNNING'
                    """, Integer.class, organizationId)).isEqualTo(2);
        } finally {
            jdbcTemplate.update("DELETE FROM async_job WHERE organization_id=?", organizationId);
            jdbcTemplate.update("DELETE FROM organization WHERE id=?", organizationId);
            jdbcTemplate.update("DELETE FROM `user` WHERE cas_id='d5-worker'");
        }
    }

    private boolean insertActiveJob(String jobId, String organizationId,
                                    CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            jdbcTemplate.update("""
                    INSERT INTO async_job(id,organization_id,job_type,target_type,target_id,status,
                      progress,attempt_count,max_attempts,created_by_cas_id)
                    VALUES (? ,?,'INVOICE_RECOGNITION','INVOICE','same-target','PENDING',
                      0,0,3,'active-job-user')
                    """, jobId, organizationId);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
