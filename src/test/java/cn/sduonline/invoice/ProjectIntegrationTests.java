package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.dto.ProjectDtos.AccessGrant;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.UpdateProjectRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.vo.ProjectVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.service.ProjectService;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ProjectIntegrationTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired ProjectService projectService;
    @Autowired OrganizationMemberMapper memberMapper;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void projectLifecycleVisibilityOptimisticLockAndAuditFormAClosedLoop() {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('project-platform','平台管理员','ACTIVE',TRUE)
                """);
        var organization = organizationService.create("project-platform",
                new CreateOrganizationRequest("项目集成测试社团", "CLUB",
                        new InitialAdmin("project-admin", "项目管理员", null, null)));
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "project-admin")) {
            memberService.create(organization.id(), "project-admin",
                    new CreateMemberRequest("project-member", "项目成员", null, null));
            memberService.create(organization.id(), "project-admin",
                    new CreateMemberRequest("project-outsider", "未授权成员", null, null));

            var project = projectService.create("project-admin", new CreateProjectRequest(
                    "迎新物资报销", "收集迎新活动发票", new BigDecimal("5000.00"), "社团经费",
                    true, "AUTHORIZED", Instant.parse("2026-08-01T00:00:00Z"),
                    Instant.parse("2026-09-01T00:00:00Z"), List.of("project-admin"),
                    List.of(new AccessGrant("project-member", Set.of("VIEW", "SUBMIT")))));

            assertThat(project.status()).isEqualTo("DRAFT");
            assertThat(project.managerCasIds()).containsExactly("project-admin");
            assertThat(project.accessGrants()).singleElement()
                    .satisfies(grant -> assertThat(grant.accessTypes()).containsExactlyInAnyOrder("VIEW", "SUBMIT"));

            var updated = projectService.update(project.id(), "project-admin",
                    new UpdateProjectRequest("迎新物资报销（第一批）", null, false,
                            null, false, null, false, null, null,
                            null, null, false, false, null, null, project.version()));
            assertThat(updated.version()).isEqualTo(1);
            assertThat(updated.name()).contains("第一批");

            assertThatThrownBy(() -> projectService.update(project.id(), "project-admin",
                    new UpdateProjectRequest("旧版本修改", null, false, null, false,
                            null, false, null, null, null, null, false, false,
                            null, null, project.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.VERSION_CONFLICT));

            var collecting = projectService.open(project.id(), "project-admin",
                    new ChangeStateRequest(updated.version()));
            var stopped = projectService.stopCollection(project.id(), "project-admin",
                    new ChangeStateRequest(collecting.version()));
            var organizing = projectService.startOrganizing(project.id(), "project-admin",
                    new ChangeStateRequest(stopped.version()));
            var archived = projectService.archive(project.id(), "project-admin",
                    new ChangeStateRequest(organizing.version()));
            assertThat(archived.status()).isEqualTo("ARCHIVED");
            assertThat(archived.archivedAt()).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE organization_id=? AND object_id=?",
                    Integer.class, organization.id(), project.id())).isEqualTo(6);
        }

        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "project-member")) {
            var visible = projectService.list(1, 20, null).records();
            assertThat(visible).hasSize(1);
            assertThat(visible.getFirst().accessGrants()).isEmpty();
            var membership = memberMapper.findByOrganizationAndCasId(
                    organization.id(), "project-member");
            membership.setTermEnd(LocalDate.of(2020, 1, 1));
            memberMapper.updateById(membership);
            assertThat(projectService.list(1, 20, null).records()).isEmpty();
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "project-outsider")) {
            assertThat(projectService.list(1, 20, null).records()).isEmpty();
        }
    }

    @Test
    @Transactional
    void projectCannotGrantMembersFromAnotherOrganizationOrSkipLifecycleStates() {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('project-platform-two','平台管理员','ACTIVE',TRUE)
                """);
        var first = organizationService.create("project-platform-two",
                new CreateOrganizationRequest("项目边界测试甲", "CLUB",
                        new InitialAdmin("project-admin-a", "管理员甲", null, null)));
        var second = organizationService.create("project-platform-two",
                new CreateOrganizationRequest("项目边界测试乙", "CLUB",
                        new InitialAdmin("project-admin-b", "管理员乙", null, null)));
        try (TenantContext.Scope ignored = TenantContext.open(first.id(), "project-admin-a")) {
            assertThatThrownBy(() -> projectService.create("project-admin-a", new CreateProjectRequest(
                    "跨租户授权", null, null, null, false, "AUTHORIZED", null, null,
                    List.of("project-admin-a"),
                    List.of(new AccessGrant("project-admin-b", Set.of("VIEW"))))))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.PROJECT_ACCESS_INVALID));

            var project = projectService.create("project-admin-a", new CreateProjectRequest(
                    "状态机测试", null, null, null, false, "ALL", null, null,
                    List.of("project-admin-a"), List.of()));
            assertThatThrownBy(() -> projectService.startOrganizing(project.id(), "project-admin-a",
                    new ChangeStateRequest(project.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.PROJECT_STATE_NOT_ALLOWED));
        }
        try (TenantContext.Scope ignored = TenantContext.open(second.id(), "project-admin-b")) {
            assertThat(projectService.list(1, 20, null).records()).isEmpty();
        }
    }

    @Test
    @Transactional
    void projectSupportsDocumentedTransitionsAndArchivesOnlyTerminalInvoices() {
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES ('project-platform-3','平台管理员','ACTIVE',TRUE)
                """);
        var organization = organizationService.create("project-platform-3",
                new CreateOrganizationRequest("项目归档测试社团", "CLUB",
                        new InitialAdmin("project-admin-c", "管理员丙", null, null)));

        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), "project-admin-c")) {
            var terminalProject = createProject("仅有终态发票");
            String applicationId = insertApplicationWithInvoice(
                    organization.id(), terminalProject.id(), "project-admin-c", "REJECTED");
            insertInvoice(organization.id(), applicationId, "project-admin-c", "VOIDED");

            var archived = projectService.archive(terminalProject.id(), "project-admin-c",
                    new ChangeStateRequest(terminalProject.version()));
            assertThat(archived.status()).isEqualTo("ARCHIVED");
            assertThat(jdbcTemplate.queryForList(
                    "SELECT status FROM invoice WHERE application_id=? ORDER BY id",
                    String.class, applicationId)).containsOnly("ARCHIVED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM application WHERE id=?", String.class, applicationId))
                    .isEqualTo("COMPLETED");

            var collectingProject = createProject("收集中直接归档");
            var collecting = projectService.open(collectingProject.id(), "project-admin-c",
                    new ChangeStateRequest(collectingProject.version()));
            assertThat(projectService.archive(collecting.id(), "project-admin-c",
                    new ChangeStateRequest(collecting.version())).status()).isEqualTo("ARCHIVED");

            var reopenedProject = createProject("整理中退回收集停止");
            var opened = projectService.open(reopenedProject.id(), "project-admin-c",
                    new ChangeStateRequest(reopenedProject.version()));
            var stopped = projectService.stopCollection(reopenedProject.id(), "project-admin-c",
                    new ChangeStateRequest(opened.version()));
            var organizing = projectService.startOrganizing(reopenedProject.id(), "project-admin-c",
                    new ChangeStateRequest(stopped.version()));
            assertThat(projectService.stopCollection(reopenedProject.id(), "project-admin-c",
                    new ChangeStateRequest(organizing.version())).status())
                    .isEqualTo("COLLECTION_STOPPED");

            var activeProject = createProject("仍有处理中发票");
            String activeApplicationId = insertApplicationWithInvoice(
                    organization.id(), activeProject.id(), "project-admin-c", "SUBMITTED");
            assertThatThrownBy(() -> projectService.archive(activeProject.id(), "project-admin-c",
                    new ChangeStateRequest(activeProject.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.PROJECT_STATE_NOT_ALLOWED));
            assertThat(projectService.detail(activeProject.id()).status()).isEqualTo("DRAFT");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM invoice WHERE application_id=?", String.class,
                    activeApplicationId)).isEqualTo("SUBMITTED");
        }
    }

    private ProjectVO createProject(String name) {
        return projectService.create("project-admin-c", new CreateProjectRequest(
                name, null, null, null, false, "ALL", null, null,
                List.of("project-admin-c"), List.of()));
    }

    private String insertApplicationWithInvoice(String organizationId, String projectId,
                                                String casId, String invoiceStatus) {
        String formId = UlidGenerator.next();
        String formVersionId = UlidGenerator.next();
        String applicationId = UlidGenerator.next();
        jdbcTemplate.update("""
                INSERT INTO application_form(id,organization_id,project_id,name,status,
                  submission_scope,max_submissions_per_user,draft_schema_json,created_by_cas_id)
                VALUES (?,?,?,'归档测试表单','PUBLISHED','ALL_MEMBERS',1,
                  JSON_OBJECT('fields',JSON_ARRAY()),?)
                """, formId, organizationId, projectId, casId);
        jdbcTemplate.update("""
                INSERT INTO form_version(id,organization_id,form_id,version_no,schema_json,
                  published_by_cas_id) VALUES (?,?,?,1,JSON_OBJECT(),?)
                """, formVersionId, organizationId, formId, casId);
        jdbcTemplate.update("""
                INSERT INTO application(id,organization_id,form_version_id,applicant_cas_id,
                  answers_json,status) VALUES (?,?,?,?,JSON_OBJECT(),?)
                """, applicationId, organizationId, formVersionId, casId,
                "SUBMITTED".equals(invoiceStatus) ? "SUBMITTED" : "REJECTED");
        insertInvoice(organizationId, applicationId, casId, invoiceStatus);
        return applicationId;
    }

    private void insertInvoice(String organizationId, String applicationId,
                               String casId, String status) {
        String fileId = UlidGenerator.next();
        jdbcTemplate.update("""
                INSERT INTO file_object(id,organization_id,uploader_cas_id,storage_key,
                  original_name,content_type,size_bytes,sha256,purpose,scan_status,ready_at)
                VALUES (?,?,?,?,'invoice.pdf','application/pdf',1,?,'INVOICE','READY',NOW(3))
                """, fileId, organizationId, casId, "project-archive/" + fileId,
                "a".repeat(64));
        jdbcTemplate.update("""
                INSERT INTO invoice(id,organization_id,application_id,invoice_type,face_amount,
                  claimed_amount,current_file_id,status)
                VALUES (?,?,?,'VAT_ELECTRONIC',100.00,100.00,?,?)
                """, UlidGenerator.next(), organizationId, applicationId, fileId, status);
    }
}
