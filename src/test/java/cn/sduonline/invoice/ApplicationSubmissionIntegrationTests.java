package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.dto.FormDtos.Condition;
import cn.sduonline.invoice.data.dto.FormDtos.CreateFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.FieldOption;
import cn.sduonline.invoice.data.dto.FormDtos.FormField;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.dto.FormDtos.FormVersionRequest;
import cn.sduonline.invoice.data.dto.FormDtos.UpdateFormRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.ApplicationFormService;
import cn.sduonline.invoice.service.ApplicationService;
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.service.ProjectService;
import cn.sduonline.invoice.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ApplicationSubmissionIntegrationTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired ProjectService projectService;
    @Autowired ApplicationFormService formService;
    @Autowired ApplicationService applicationService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void applicantDraftIsIdempotentVersionBoundValidatedAndSubmissionLimited() throws Exception {
        Fixture fixture = fixture("app-platform", "app-admin", "app-member", "app-other", "申请闭环社团");
        String projectId;
        String formId;
        long collectingVersion;
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-admin")) {
            var project = projectService.create("app-admin", new CreateProjectRequest(
                    "申请闭环项目", null, null, null, false, "ALL", null, null,
                    List.of("app-admin"), List.of()));
            projectId = project.id();
            var form = formService.create(projectId, "app-admin", new CreateFormRequest(
                    "差旅申请", "ALL_MEMBERS", null, null, 1, schema("申请用途")));
            formService.publish(form.id(), "app-admin", new FormVersionRequest(form.version()));
            formId = form.id();
            collectingVersion = projectService.open(projectId, "app-admin",
                    new ChangeStateRequest(project.version())).version();
        }

        String applicationId;
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-member")) {
            assertThat(applicationService.availableForms()).extracting("id").containsExactly(formId);
            var draft = applicationService.createDraft(formId, "app-member");
            applicationId = draft.id();
            assertThat(applicationService.createDraft(formId, "app-member").id()).isEqualTo(applicationId);
            assertThat(draft.formVersionNo()).isEqualTo(1);

            var firstSave = applicationService.saveDraft(applicationId, "app-member",
                    new SaveDraftRequest(json("{\"purpose\":\"参加会议\"}"), draft.version()));
            assertThat(firstSave.version()).isEqualTo(1);
            assertThatThrownBy(() -> applicationService.submit(applicationId, "app-member",
                    new SubmitRequest(firstSave.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.PARAM_INVALID));
        }

        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-admin")) {
            var current = formService.detail(formId);
            var changed = formService.update(formId, "app-admin", new UpdateFormRequest(
                    null, null, null, null, false, false, null,
                    schema("申请用途（新版本）"), current.version()));
            formService.publish(formId, "app-admin", new FormVersionRequest(changed.version()));
        }

        long savedVersion;
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-member")) {
            assertThat(applicationService.availableForm(formId).formVersionNo()).isEqualTo(2);
            assertThat(applicationService.detail(applicationId).schema().fields().getFirst().label())
                    .isEqualTo("申请用途");
            var completed = applicationService.saveDraft(applicationId, "app-member",
                    new SaveDraftRequest(json("""
                            {"purpose":"参加会议","amount":128.50,"category":"OTHER","detail":"外地交通"}
                            """), 1L));
            savedVersion = completed.version();
            assertThatThrownBy(() -> applicationService.saveDraft(applicationId, "app-member",
                    new SaveDraftRequest(json("{\"purpose\":\"旧写入\"}"), 1L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.VERSION_CONFLICT));
        }

        long stoppedVersion;
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-admin")) {
            stoppedVersion = projectService.stopCollection(projectId, "app-admin",
                    new ChangeStateRequest(collectingVersion)).version();
        }
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-member")) {
            assertThatThrownBy(() -> applicationService.submit(applicationId, "app-member",
                    new SubmitRequest(savedVersion)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.SUBMISSION_WINDOW_CLOSED));
        }
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-admin")) {
            projectService.open(projectId, "app-admin", new ChangeStateRequest(stoppedVersion));
        }
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-member")) {
            var submitted = applicationService.submit(applicationId, "app-member",
                    new SubmitRequest(savedVersion));
            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            assertThat(applicationService.submit(applicationId, "app-member",
                    new SubmitRequest(savedVersion)).id()).isEqualTo(applicationId);
            assertThatThrownBy(() -> applicationService.createDraft(formId, "app-member"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.SUBMISSION_LIMIT_REACHED));
            var revisions = applicationService.revisions(applicationId);
            assertThat(revisions).hasSize(2);
            assertThat(revisions.getFirst().changedFields())
                    .containsExactlyInAnyOrder("amount", "category", "detail");
            assertThat(revisions.getLast().changedFields()).containsExactly("purpose");
        }
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "app-other")) {
            assertThatThrownBy(() -> applicationService.detail(applicationId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.APPLICATION_NOT_FOUND));
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE organization_id=? AND object_type='APPLICATION'
                """, Integer.class, fixture.organizationId())).isEqualTo(4);
    }

    @Test
    @Transactional
    void projectAuthorizedFormIsHiddenWithoutSubmitGrant() throws Exception {
        Fixture fixture = fixture("scope-platform", "scope-admin", "scope-member", "scope-other",
                "申请范围社团");
        String formId;
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "scope-admin")) {
            var project = projectService.create("scope-admin", new CreateProjectRequest(
                    "范围项目", null, null, null, false, "ALL", null, null,
                    List.of("scope-admin"), List.of()));
            var form = formService.create(project.id(), "scope-admin", new CreateFormRequest(
                    "授权申请表", "PROJECT_AUTHORIZED", null, null, 1, schema("用途")));
            formService.publish(form.id(), "scope-admin", new FormVersionRequest(form.version()));
            projectService.open(project.id(), "scope-admin", new ChangeStateRequest(project.version()));
            formId = form.id();
        }
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "scope-member")) {
            assertThat(applicationService.availableForms()).isEmpty();
            assertThatThrownBy(() -> applicationService.createDraft(formId, "scope-member"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.NO_PERMISSION));
        }
    }

    private Fixture fixture(String platformId, String adminId, String memberId,
                            String otherId, String organizationName) {
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platformId, "平台管理员", "ACTIVE");
        var organization = organizationService.create(platformId,
                new CreateOrganizationRequest(organizationName, "CLUB",
                        new InitialAdmin(adminId, "申请管理员", null, null)));
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), adminId)) {
            memberService.create(organization.id(), adminId,
                    new CreateMemberRequest(memberId, "申请成员", null, null));
            memberService.create(organization.id(), adminId,
                    new CreateMemberRequest(otherId, "其他成员", null, null));
        }
        return new Fixture(organization.id());
    }

    private FormSchema schema(String purposeLabel) throws Exception {
        return new FormSchema(List.of(
                new FormField("purpose", "TEXT", purposeLabel, true, null,
                        null, null, null, null, null, null),
                new FormField("amount", "MONEY", "申请金额", true, null,
                        null, null, null, new BigDecimal("0.01"), new BigDecimal("10000.00"), null),
                new FormField("category", "SINGLE_SELECT", "类型", true, null,
                        null, null, List.of(new FieldOption("TRAVEL", "交通"),
                        new FieldOption("OTHER", "其他")), null, null, null),
                new FormField("detail", "TEXT", "补充说明", false, null,
                        null, new Condition("category", "EQUALS", json("\"OTHER\"")),
                        null, null, null, null)));
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private record Fixture(String organizationId) {
    }
}
