package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.dto.FormDtos.*;
import cn.sduonline.invoice.data.dto.InvoiceDtos.*;
import cn.sduonline.invoice.data.dto.RecognitionDtos.*;
import cn.sduonline.invoice.data.dto.OrganizationDtos.*;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.*;
import cn.sduonline.invoice.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
@Import(InvoiceWorkflowIntegrationTests.RecognitionTestConfiguration.class)
class InvoiceWorkflowIntegrationTests {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired ProjectService projectService;
    @Autowired ApplicationFormService formService;
    @Autowired ApplicationService applicationService;
    @Autowired FileObjectService fileService;
    @Autowired InvoiceService invoiceService;
    @Autowired ObjectMapper objectMapper;
    @Autowired RecognitionService recognitionService;
    @Autowired RecognitionWorker recognitionWorker;
    @Autowired TestRecognitionAdapter recognitionAdapter;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void readyFilesDriveInvoiceRevisionAttachmentAndApplicationSubmission() throws Exception {
        String platform = "inv-platform";
        String admin = "inv-admin";
        String member = "inv-member";
        String other = "inv-other";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platform, "平台管理员", "ACTIVE");
        var organization = organizationService.create(platform,
                new CreateOrganizationRequest("发票闭环社团", "CLUB",
                        new InitialAdmin(admin, "发票管理员", null, null)));
        String formId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(member, "申请人", null, null));
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(other, "其他人", null, null));
            var project = projectService.create(admin, new CreateProjectRequest(
                    "发票测试项目", null, null, null, false, "ALL", null, null,
                    List.of(admin), List.of()));
            var form = formService.create(project.id(), admin, new CreateFormRequest(
                    "发票申请", "ALL_MEMBERS", null, null, 1,
                    new FormSchema(List.of(new FormField("invoices", "INVOICE", "发票", true,
                            null, null, null, null, null, null, null)))));
            formService.publish(form.id(), admin, new FormVersionRequest(form.version()));
            projectService.open(project.id(), admin, new ChangeStateRequest(project.version()));
            formId = form.id();
        }

        String applicationId;
        String invoiceId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            var application = applicationService.createDraft(formId, member);
            applicationId = application.id();
            var original = register(member, "invoice.pdf", "application/pdf", "a", "INVOICE_ORIGINAL");
            assertThatThrownBy(() -> invoiceService.create(applicationId, member,
                    invoiceRequest(original.id(), "100.00", "80.00")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.FILE_NOT_READY));
        }

        var original = fileService.inspect(platform, organization.id(),
                fileIdByHash(organization.id(), "a"), new InspectFileRequest("READY", null));
        String recognitionJobId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            assertThatThrownBy(() -> invoiceService.create(applicationId, member,
                    invoiceRequest(original.id(), "100.00", "120.00")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.INVOICE_AMOUNT_INVALID));
            var invoice = invoiceService.create(applicationId, member,
                    invoiceRequest(original.id(), "100.00", "80.00"));
            invoiceId = invoice.id();
            assertThat(invoice.fileRevisions()).hasSize(1);
            assertThat(invoice.fileRevisions().getFirst().revisionNo()).isEqualTo(1);
            assertThatThrownBy(() -> invoiceService.create(applicationId, member,
                    invoiceRequest(original.id(), "100.00", "70.00")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.FILE_ALREADY_USED));
            var recognitionJob = recognitionService.start(invoiceId, member);
            recognitionJobId = recognitionJob.id();
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("PENDING_RECOGNITION");
            assertThat(recognitionService.start(invoiceId, member).id()).isEqualTo(recognitionJob.id());
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            assertThatThrownBy(() -> recognitionService.start(invoiceId, admin))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.INVOICE_NOT_FOUND));
        }
        recognitionAdapter.failNext();
        assertThat(recognitionWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(status,':',COALESCE(error_code,''),':',COALESCE(error_message,'')) FROM async_job WHERE id=?",
                String.class, recognitionJobId)).startsWith("PENDING:IllegalStateException:");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("DRAFT");
        }
        jdbcTemplate.update("UPDATE async_job SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",
                recognitionJobId);
        assertThat(recognitionWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(status,':',COALESCE(error_code,''),':',COALESCE(error_message,'')) FROM async_job WHERE id=?",
                String.class, recognitionJobId)).isEqualTo("SUCCEEDED::");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("PENDING_RECOGNITION");
            var firstSuggestions = recognitionService.suggestions(invoiceId).stream()
                    .filter(item -> item.status().equals("PENDING")).toList();
            assertThat(firstSuggestions).extracting("fieldPath")
                    .containsExactlyInAnyOrder("sellerName", "faceAmount");
            recognitionService.confirm(invoiceId, member, new ConfirmSuggestionsRequest(
                    firstSuggestions.stream().map(item ->
                            new SuggestionDecision(item.id(), "ACCEPTED", null)).toList()));

            var secondJob = recognitionService.start(invoiceId, member);
            assertThat(secondJob.id()).isNotEqualTo(recognitionJobId);
            var pending = invoiceService.detail(invoiceId);
            assertThat(pending.status()).isEqualTo("PENDING_RECOGNITION");
            var edited = invoiceService.update(invoiceId, member,
                    invoiceUpdate(pending.version(), "手工供应商"));
            assertThat(edited.status()).isEqualTo("PENDING_RECOGNITION");
        }
        assertThat(recognitionWorker.processNext()).isTrue();
        String secondJobId = jdbcTemplate.queryForObject("""
                SELECT id FROM async_job WHERE organization_id=? AND target_id=?
                ORDER BY created_at DESC,id DESC LIMIT 1
                """, String.class, organization.id(), invoiceId);
        assertThat(secondJobId).isNotEqualTo(recognitionJobId);
        String sellerSuggestionId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            var suggestions = recognitionService.suggestions(invoiceId).stream()
                    .filter(item -> item.status().equals("PENDING")).toList();
            assertThat(suggestions).extracting("fieldPath")
                    .containsExactlyInAnyOrder("sellerName", "faceAmount");
            sellerSuggestionId = suggestions.stream()
                    .filter(item -> item.fieldPath().equals("sellerName")).findFirst().orElseThrow().id();
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            String deniedSuggestionId = sellerSuggestionId;
            assertThatThrownBy(() -> recognitionService.confirm(invoiceId, admin,
                    new ConfirmSuggestionsRequest(List.of(
                            new SuggestionDecision(deniedSuggestionId, "ACCEPTED", null)))))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.INVOICE_NOT_FOUND));
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            recognitionService.confirm(invoiceId, member, new ConfirmSuggestionsRequest(List.of(
                    new SuggestionDecision(sellerSuggestionId, "CORRECTED", "修正供应商"))));
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("PENDING_RECOGNITION");
            assertThat(recognitionService.suggestions(invoiceId).stream()
                    .filter(item -> item.id().equals(sellerSuggestionId)).findFirst().orElseThrow()
                    .finalValue()).isEqualTo("修正供应商");
            var remaining = recognitionService.suggestions(invoiceId).stream()
                    .filter(item -> item.status().equals("PENDING")).toList();
            recognitionService.confirm(invoiceId, member, new ConfirmSuggestionsRequest(
                    remaining.stream().map(item ->
                            new SuggestionDecision(item.id(), "ACCEPTED", null)).toList()));
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("DRAFT");
            register(member, "payment.png", "image/png", "b", "PAYMENT_RECORD");
            register(member, "replacement.ofd", "application/ofd", "c", "INVOICE_ORIGINAL");
            register(member, "pending-submit.pdf", "application/pdf", "d", "INVOICE_ORIGINAL");
        }

        var payment = fileService.inspect(platform, organization.id(), fileIdByHash(organization.id(), "b"),
                new InspectFileRequest("READY", null));
        var replacement = fileService.inspect(platform, organization.id(), fileIdByHash(organization.id(), "c"),
                new InspectFileRequest("READY", null));
        var pendingSubmitFile = fileService.inspect(platform, organization.id(), fileIdByHash(organization.id(), "d"),
                new InspectFileRequest("READY", null));
        String pendingSubmitInvoiceId;
        String pendingSubmitJobId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            long currentVersion = invoiceService.detail(invoiceId).version();
            var attachment = invoiceService.addAttachment(invoiceId, member,
                    new AddAttachmentRequest(currentVersion, "PAYMENT_RECORD", payment.id(), "付款截图"));
            assertThat(attachment.status()).isEqualTo("ACTIVE");
            currentVersion = invoiceService.detail(invoiceId).version();
            var replaced = invoiceService.replaceFile(invoiceId, member,
                    new ReplaceFileRequest(currentVersion, replacement.id(), "原文件上传错误"));
            assertThat(replaced.fileRevisions()).extracting("revisionNo").containsExactly(1, 2);

            var pendingSubmitInvoice = invoiceService.create(applicationId, member,
                    invoiceRequest(pendingSubmitFile.id(), "50.00", "40.00"));
            pendingSubmitInvoiceId = pendingSubmitInvoice.id();
            pendingSubmitJobId = recognitionService.start(pendingSubmitInvoiceId, member).id();
            assertThat(invoiceService.detail(pendingSubmitInvoiceId).status())
                    .isEqualTo("PENDING_RECOGNITION");

            var saved = applicationService.saveDraft(applicationId, member,
                    new SaveDraftRequest(objectMapper.readTree(
                            "{\"invoices\":[\"" + invoiceId + "\",\""
                                    + pendingSubmitInvoiceId + "\"]}"), 0L));
            var submitted = applicationService.submit(applicationId, member,
                    new SubmitRequest(saved.version()));
            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            var submittedInvoice = invoiceService.detail(invoiceId);
            assertThat(submittedInvoice.status()).isEqualTo("SUBMITTED");
            assertThat(invoiceService.detail(pendingSubmitInvoiceId).status()).isEqualTo("SUBMITTED");
            assertThatThrownBy(() -> recognitionService.start(invoiceId, member))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.INVOICE_STATE_NOT_ALLOWED));
            assertThatThrownBy(() -> invoiceService.deleteDraft(invoiceId, member,
                    submittedInvoice.version()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.INVOICE_STATE_NOT_ALLOWED));
        }
        assertThat(recognitionWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.outcome'))
                FROM async_job WHERE id=?
                """, String.class, pendingSubmitJobId)).isEqualTo("DISCARDED_SUBMITTED");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            assertThat(recognitionService.suggestions(pendingSubmitInvoiceId)).isEmpty();
            jdbcTemplate.update("UPDATE invoice SET status='RETURNED' WHERE id=?", invoiceId);
            jdbcTemplate.update("UPDATE application SET status='RETURNED' WHERE id=?", applicationId);
            recognitionService.start(invoiceId, member);
            assertThat(invoiceService.detail(invoiceId).status()).isEqualTo("RETURNED");

            var voided = invoiceService.voidFormal(invoiceId, member,
                    new VersionedReasonRequest(invoiceService.detail(invoiceId).version(), "发票已冲红"));
            assertThat(voided.status()).isEqualTo("VOIDED");
            invoiceService.voidFormal(pendingSubmitInvoiceId, member,
                    new VersionedReasonRequest(invoiceService.detail(pendingSubmitInvoiceId).version(),
                            "发票已冲红"));
            assertThat(applicationService.detail(applicationId).status()).isEqualTo("REJECTED");
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), other)) {
            assertThatThrownBy(() -> invoiceService.detail(invoiceId))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.APPLICATION_NOT_FOUND));
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE organization_id=?
                  AND action IN ('FILE_INSPECTION_RECORDED','INVOICE_DRAFT_CREATED',
                    'INVOICE_FILE_REPLACED','INVOICE_ATTACHMENT_ADDED','INVOICE_VOIDED')
                """, Integer.class, organization.id())).isEqualTo(10);
    }

    private cn.sduonline.invoice.data.vo.FileObjectVO register(
            String actor, String name, String type, String hashChar, String purpose) {
        return fileService.register(actor, new RegisterFileRequest(
                name, type, 1024L, hashChar.repeat(64), purpose));
    }

    private String fileIdByHash(String organizationId, String hashChar) {
        return jdbcTemplate.queryForObject("SELECT id FROM file_object WHERE organization_id=? AND sha256=?",
                String.class, organizationId, hashChar.repeat(64));
    }

    private CreateInvoiceRequest invoiceRequest(String fileId, String face, String claimed) {
        return new CreateInvoiceRequest("VAT_ELECTRONIC", "3700", "10001", null,
                LocalDate.of(2026, 8, 1), "山东大学", null, "测试供应商", null,
                new BigDecimal(face), new BigDecimal(claimed), fileId, null);
    }

    private UpdateInvoiceRequest invoiceUpdate(long version, String sellerName) {
        return new UpdateInvoiceRequest(version, null, null, null, null,
                null, null, null, sellerName, null, null, null, null, Set.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecognitionTestConfiguration {
        @Bean
        @Primary
        ObjectStorage recognitionObjectStorage() {
            return new ObjectStorage() {
                @Override public UploadGrant createUploadGrant(String key, String contentType,
                                                                 long sizeBytes, String sha256) {
                    throw new UnsupportedOperationException();
                }
                @Override public Optional<StoredObject> headUpload(String key) { return Optional.empty(); }
                @Override public void finalizeUpload(String key) { }
                @Override public void downloadTo(String key, Path target) {
                    try {
                        Files.writeString(target, "%PDF-1.7\nrecognition\n%%EOF",
                                StandardCharsets.US_ASCII);
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
                @Override public void delete(String key) { }
                @Override public DownloadGrant createDownloadGrant(String key, String originalName,
                                                                     String contentType) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        @Primary
        TestRecognitionAdapter recognitionAdapter() {
            return new TestRecognitionAdapter();
        }
    }

    static class TestRecognitionAdapter implements InvoiceRecognitionAdapter {
        private final AtomicBoolean failNext = new AtomicBoolean();

        void failNext() {
            failNext.set(true);
        }

        @Override
        public RecognitionResult recognize(Path file, String contentType) {
            if (failNext.getAndSet(false)) throw new IllegalStateException("test recognition failure");
            return new RecognitionResult("山东大学 测试供应商 100.00", "qr-original",
                    Map.of(
                            "sellerName", new SuggestedField(
                                    "测试供应商", new BigDecimal("0.9800")),
                            "faceAmount", new SuggestedField(
                                    "100.00", new BigDecimal("0.9900")),
                            "ignoredField", new SuggestedField(
                                    "ignored", BigDecimal.ONE)), true);
        }
    }
}
