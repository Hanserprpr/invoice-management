package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.FormDtos.Condition;
import cn.sduonline.invoice.data.dto.FormDtos.CopyFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.CreateFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.FieldOption;
import cn.sduonline.invoice.data.dto.FormDtos.FormField;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.dto.FormDtos.FormVersionRequest;
import cn.sduonline.invoice.data.dto.FormDtos.UpdateFormRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.ApplicationFormService;
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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ApplicationFormIntegrationTests {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired ProjectService projectService;
    @Autowired ApplicationFormService formService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void draftsPublishImmutableVersionsAndFollowLifecycle() {
        var fixture = createFixture("form-platform", "form-admin", "表单版本测试社团");
        try (TenantContext.Scope ignored = TenantContext.open(fixture.organizationId(), "form-admin")) {
            var project = createProject("表单项目一", "form-admin");
            var targetProject = createProject("表单项目二", "form-admin");
            var draft = formService.create(project.id(), "form-admin", new CreateFormRequest(
                    "发票报销申请", "PROJECT_AUTHORIZED", null, null, 2, schema("用途说明")));
            assertThat(draft.status()).isEqualTo("DRAFT");
            assertThat(draft.latestPublishedVersionNo()).isZero();
            assertThat(draft.hasUnpublishedChanges()).isTrue();

            var firstVersion = formService.publish(draft.id(), "form-admin",
                    new FormVersionRequest(draft.version()));
            assertThat(firstVersion.versionNo()).isEqualTo(1);
            assertThat(firstVersion.schema().fields()).hasSize(2);
            assertThat(firstVersion.dictionarySnapshot().has("EXPENSE_CATEGORY")).isTrue();

            var published = formService.detail(draft.id());
            assertThat(published.status()).isEqualTo("PUBLISHED");
            assertThat(published.hasUnpublishedChanges()).isFalse();
            var changed = formService.update(draft.id(), "form-admin", new UpdateFormRequest(
                    null, null, null, null, false, false, null,
                    schema("报销用途（新版）"), published.version()));
            assertThat(changed.hasUnpublishedChanges()).isTrue();

            assertThatThrownBy(() -> formService.update(draft.id(), "form-admin",
                    new UpdateFormRequest("旧版本写入", null, null, null, false, false,
                            null, null, published.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.VERSION_CONFLICT));

            var secondVersion = formService.publish(draft.id(), "form-admin",
                    new FormVersionRequest(changed.version()));
            assertThat(secondVersion.versionNo()).isEqualTo(2);
            assertThat(formService.version(draft.id(), 1).schema().fields().getFirst().label())
                    .isEqualTo("用途说明");
            assertThat(formService.version(draft.id(), 2).schema().fields().getFirst().label())
                    .isEqualTo("报销用途（新版）");

            var copied = formService.copy(targetProject.id(), "form-admin",
                    new CopyFormRequest(draft.id(), "复制的申请表"));
            assertThat(copied.status()).isEqualTo("DRAFT");
            assertThat(copied.startsAt()).isNull();
            assertThat(copied.draftSchema().fields().getFirst().label()).isEqualTo("报销用途（新版）");

            var current = formService.detail(draft.id());
            var paused = formService.pause(draft.id(), "form-admin",
                    new FormVersionRequest(current.version()));
            var resumed = formService.resume(draft.id(), "form-admin",
                    new FormVersionRequest(paused.version()));
            var ended = formService.end(draft.id(), "form-admin",
                    new FormVersionRequest(resumed.version()));
            assertThat(ended.status()).isEqualTo("ENDED");
            assertThatThrownBy(() -> formService.update(draft.id(), "form-admin",
                    new UpdateFormRequest("禁止修改", null, null, null, false, false,
                            null, null, ended.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.FORM_STATE_NOT_ALLOWED));

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM audit_log
                    WHERE organization_id=? AND object_type IN ('APPLICATION_FORM','FORM_VERSION')
                    """, Integer.class, fixture.organizationId())).isEqualTo(8);
        }
    }

    @Test
    @Transactional
    void rejectsForwardConditionsEmptyPublishingAndCrossTenantReads() throws Exception {
        var first = createFixture("form-b-plat", "form-b-admin", "表单边界甲");
        String formId;
        try (TenantContext.Scope ignored = TenantContext.open(first.organizationId(), "form-b-admin")) {
            var project = createProject("边界项目", "form-b-admin");
            var invalidSchema = new FormSchema(List.of(new FormField(
                    "reason", "TEXT", "原因", true, null,
                    new Condition("future_field", "EQUALS", objectMapper.readTree("\"yes\"")),
                    null, null, null, null, null)));
            assertThatThrownBy(() -> formService.create(project.id(), "form-b-admin",
                    new CreateFormRequest("非法条件表单", "ALL_MEMBERS", null, null, 1, invalidSchema)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.PARAM_INVALID));

            var empty = formService.create(project.id(), "form-b-admin",
                    new CreateFormRequest("空草稿", "ALL_MEMBERS", null, null, 1,
                            new FormSchema(List.of())));
            formId = empty.id();
            assertThatThrownBy(() -> formService.publish(empty.id(), "form-b-admin",
                    new FormVersionRequest(empty.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.PARAM_INVALID));
        }

        var second = createFixture("form-b-plat-2", "form-b-admin-2", "表单边界乙");
        try (TenantContext.Scope ignored = TenantContext.open(second.organizationId(), "form-b-admin-2")) {
            assertThatThrownBy(() -> formService.detail(formId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.FORM_NOT_FOUND));
        }
    }

    private Fixture createFixture(String platformCasId, String adminCasId, String organizationName) {
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platformCasId, "平台管理员", "ACTIVE");
        var organization = organizationService.create(platformCasId,
                new CreateOrganizationRequest(organizationName, "CLUB",
                        new InitialAdmin(adminCasId, "表单管理员", null, null)));
        return new Fixture(organization.id());
    }

    private cn.sduonline.invoice.data.vo.ProjectVO createProject(String name, String adminCasId) {
        return projectService.create(adminCasId, new CreateProjectRequest(
                name, null, null, null, false, "ALL", null, null,
                List.of(adminCasId), List.of()));
    }

    private FormSchema schema(String firstLabel) {
        return new FormSchema(List.of(
                new FormField("purpose", "TEXT", firstLabel, true, "请说明用途",
                        null, null, null, null, null, null),
                new FormField("category", "SINGLE_SELECT", "费用类别", true, null,
                        null, null,
                        List.of(new FieldOption("MATERIAL", "物资"),
                                new FieldOption("TRANSPORT", "交通")),
                        null, null, null)));
    }

    private record Fixture(String organizationId) {
    }
}
