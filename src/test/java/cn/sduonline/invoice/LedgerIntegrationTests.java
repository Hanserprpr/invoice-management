package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.dto.FormDtos.*;
import cn.sduonline.invoice.data.dto.InvoiceDtos.CreateInvoiceRequest;
import cn.sduonline.invoice.data.dto.LedgerDtos.*;
import cn.sduonline.invoice.data.dto.OrganizationDtos.*;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.dto.ReviewDtos.ApproveReviewRequest;
import cn.sduonline.invoice.data.dto.ReviewDtos.StartReviewRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.*;
import cn.sduonline.invoice.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
class LedgerIntegrationTests {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired ProjectService projectService;
    @Autowired ApplicationFormService formService;
    @Autowired ApplicationService applicationService;
    @Autowired FileObjectService fileService;
    @Autowired InvoiceService invoiceService;
    @Autowired ReviewService reviewService;
    @Autowired LedgerService ledgerService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void ledgerTotalsVisibilitySavedFiltersAndTimelineRemainScoped() throws Exception {
        String platform = "ledger-platform";
        String admin = "ledger-admin";
        String memberOne = "ledger-member-1";
        String memberTwo = "ledger-member-2";
        String viewer = "ledger-viewer";
        String ordinary = "ledger-ordinary";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platform, "平台管理员", "ACTIVE");
        var organization = organizationService.create(platform,
                new CreateOrganizationRequest("台账测试社团", "CLUB",
                        new InitialAdmin(admin, "台账管理员", null, null)));
        String projectId;
        String formId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            for (String casId : List.of(memberOne, memberTwo, viewer, ordinary)) {
                memberService.create(organization.id(), admin,
                        new CreateMemberRequest(casId, casId, null, null));
            }
            var project = projectService.create(admin, new CreateProjectRequest(
                    "台账项目", null, null, null, false, "ALL", null, null,
                    List.of(admin), List.of()));
            projectId = project.id();
            memberService.replaceRoles(organization.id(), viewer, admin,
                    new ReplaceRolesRequest(0L,
                            List.of(new RoleAssignment("MEMBER", null, null)),
                            List.of(new ProjectGrant(projectId, Set.of("VIEW")))));
            var form = formService.create(projectId, admin, new CreateFormRequest(
                    "台账申请表", "ALL_MEMBERS", null, null, 1,
                    new FormSchema(List.of(new FormField("invoices", "INVOICE", "发票", true,
                            null, null, null, null, null, null, null)))));
            formService.publish(form.id(), admin, new FormVersionRequest(form.version()));
            projectService.open(projectId, admin, new ChangeStateRequest(project.version()));
            formId = form.id();
        }

        String fileOne;
        String fileTwo;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), memberOne)) {
            fileOne = register(memberOne, "ledger-one.pdf", "h");
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), memberTwo)) {
            fileTwo = register(memberTwo, "ledger-two.pdf", "i");
        }
        fileService.inspect(platform, organization.id(), fileOne, new InspectFileRequest("READY", null));
        fileService.inspect(platform, organization.id(), fileTwo, new InspectFileRequest("READY", null));
        String invoiceOne = submitInvoice(organization.id(), formId, memberOne, fileOne,
                "L-001", "100.00", "80.00");
        String invoiceTwo = submitInvoice(organization.id(), formId, memberTwo, fileTwo,
                "L-002", "200.00", "150.00");

        String categoryId = dictionaryItem(organization.id(), "EXPENSE_CATEGORY", "TRANSPORT");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            reviewService.start(invoiceOne, admin, new StartReviewRequest(1L));
            reviewService.approve(invoiceOne, admin,
                    new ApproveReviewRequest(2L, categoryId, "台账已整理", null));
        }

        LedgerFilter noFilter = emptyFilter();
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), memberOne)) {
            var own = ledgerService.invoices(1, 20, noFilter, "updatedAt", "DESC");
            assertThat(own.records()).extracting("invoiceId").containsExactly(invoiceOne);
            assertThat(own.total()).isEqualTo(1);
            assertThat(own.totalFaceAmount()).isEqualByComparingTo("100.00");
            assertThat(own.totalClaimedAmount()).isEqualByComparingTo("80.00");
            assertThat(own.records().getFirst().paperStatus()).isEqualTo("NOT_REQUIRED");
            assertThat(ledgerService.timeline(invoiceOne)).extracting("eventType")
                    .contains("FILE", "AUDIT", "REVIEW");
            assertThatThrownBy(() -> ledgerService.timeline(invoiceTwo))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.INVOICE_NOT_FOUND));

            var first = ledgerService.createSavedFilter(new CreateSavedFilterRequest(
                    "我的发票", new LedgerFilter(null, null, memberOne,
                    null, null, null, null, null, null, null, null, null, null), true));
            var second = ledgerService.createSavedFilter(new CreateSavedFilterRequest(
                    "已通过", new LedgerFilter(null, null, null, null, null,
                    null, null, null, Set.of("INTERNALLY_APPROVED"), null, null, null, null), true));
            assertThat(ledgerService.savedFilters()).filteredOn("isDefault", true)
                    .extracting("id").containsExactly(second.id());
            assertThatThrownBy(() -> ledgerService.createSavedFilter(new CreateSavedFilterRequest(
                    "已通过", noFilter, false)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.SAVED_FILTER_ALREADY_EXISTS));
            var renamed = ledgerService.updateSavedFilter(second.id(),
                    new UpdateSavedFilterRequest("已通过发票", null, null, 0L));
            assertThat(renamed.version()).isEqualTo(1);
            assertThatThrownBy(() -> ledgerService.updateSavedFilter(second.id(),
                    new UpdateSavedFilterRequest("旧版本", null, null, 0L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.VERSION_CONFLICT));
            ledgerService.deleteSavedFilter(first.id(), 0L);
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), ordinary)) {
            assertThat(ledgerService.invoices(1, 20, noFilter, null, null).records()).isEmpty();
            assertThat(ledgerService.savedFilters()).isEmpty();
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), viewer)) {
            var projectRows = ledgerService.invoices(1, 1, noFilter, "claimedAmount", "DESC");
            assertThat(projectRows.records()).hasSize(1);
            assertThat(projectRows.records().getFirst().invoiceId()).isEqualTo(invoiceTwo);
            assertThat(projectRows.total()).isEqualTo(2);
            assertThat(projectRows.totalFaceAmount()).isEqualByComparingTo("300.00");
            assertThat(projectRows.totalClaimedAmount()).isEqualByComparingTo("230.00");
            assertThat(ledgerService.timeline(invoiceOne)).isNotEmpty();
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            LedgerFilter approvedOnly = new LedgerFilter(projectId, null, null,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1),
                    new BigDecimal("70.00"), new BigDecimal("100.00"), categoryId,
                    Set.of("INTERNALLY_APPROVED"), null, "VAT_ELECTRONIC", "NOT_REQUIRED", "L-001");
            var filtered = ledgerService.invoices(1, 20, approvedOnly, "invoiceDate", "ASC");
            assertThat(filtered.records()).extracting("invoiceId").containsExactly(invoiceOne);
            assertThat(filtered.totalClaimedAmount()).isEqualByComparingTo("80.00");
        }
    }

    private String submitInvoice(String organizationId, String formId, String member,
                                 String fileId, String number, String face, String claimed) throws Exception {
        try (TenantContext.Scope ignored = TenantContext.open(organizationId, member)) {
            var application = applicationService.createDraft(formId, member);
            var invoice = invoiceService.create(application.id(), member,
                    new CreateInvoiceRequest("VAT_ELECTRONIC", "3700", number, null,
                            LocalDate.of(2026, 8, 1), "山东大学", null, "台账供应商", null,
                            new BigDecimal(face), new BigDecimal(claimed), fileId, null));
            var saved = applicationService.saveDraft(application.id(), member,
                    new SaveDraftRequest(objectMapper.readTree(
                            "{\"invoices\":[\"" + invoice.id() + "\"]}"), 0L));
            applicationService.submit(application.id(), member, new SubmitRequest(saved.version()));
            return invoice.id();
        }
    }

    private String register(String actor, String name, String hashChar) {
        return fileService.register(actor, new RegisterFileRequest(
                name, "application/pdf", 1024L, hashChar.repeat(64), "INVOICE_ORIGINAL")).id();
    }

    private String dictionaryItem(String organizationId, String type, String code) {
        return jdbcTemplate.queryForObject("""
                SELECT di.id FROM dictionary_item di
                JOIN dictionary_version dv ON dv.id=di.dictionary_version_id
                WHERE di.organization_id=? AND dv.dictionary_type=? AND di.code=?
                """, String.class, organizationId, type, code);
    }

    private LedgerFilter emptyFilter() {
        return new LedgerFilter(null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
