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
            assertThat(recognitionService.start(invoiceId, member).id()).isEqualTo(recognitionJob.id());
        }
        assertThat(recognitionWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(status,':',COALESCE(error_code,''),':',COALESCE(error_message,'')) FROM async_job WHERE id=?",
                String.class, recognitionJobId)).isEqualTo("SUCCEEDED::");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            var suggestions = recognitionService.suggestions(invoiceId);
            assertThat(suggestions).extracting("fieldPath")
                    .containsExactlyInAnyOrder("sellerName", "faceAmount");
            var sellerSuggestion = suggestions.stream()
                    .filter(item -> item.fieldPath().equals("sellerName")).findFirst().orElseThrow();
            recognitionService.confirm(invoiceId, member, new ConfirmSuggestionsRequest(List.of(
                    new SuggestionDecision(sellerSuggestion.id(), "CORRECTED", "修正供应商"))));
            assertThat(recognitionService.suggestions(invoiceId).stream()
                    .filter(item -> item.id().equals(sellerSuggestion.id())).findFirst().orElseThrow()
                    .finalValue()).isEqualTo("修正供应商");
            register(member, "payment.png", "image/png", "b", "PAYMENT_RECORD");
            register(member, "replacement.ofd", "application/ofd", "c", "INVOICE_ORIGINAL");
        }

        var payment = fileService.inspect(platform, organization.id(), fileIdByHash(organization.id(), "b"),
                new InspectFileRequest("READY", null));
        var replacement = fileService.inspect(platform, organization.id(), fileIdByHash(organization.id(), "c"),
                new InspectFileRequest("READY", null));
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), member)) {
            var attachment = invoiceService.addAttachment(invoiceId, member,
                    new AddAttachmentRequest(0L, "PAYMENT_RECORD", payment.id(), "付款截图"));
            assertThat(attachment.status()).isEqualTo("ACTIVE");
            var replaced = invoiceService.replaceFile(invoiceId, member,
                    new ReplaceFileRequest(1L, replacement.id(), "原文件上传错误"));
            assertThat(replaced.version()).isEqualTo(2);
            assertThat(replaced.fileRevisions()).extracting("revisionNo").containsExactly(1, 2);

            var saved = applicationService.saveDraft(applicationId, member,
                    new SaveDraftRequest(objectMapper.readTree(
                            "{\"invoices\":[\"" + invoiceId + "\"]}"), 0L));
            var submitted = applicationService.submit(applicationId, member,
                    new SubmitRequest(saved.version()));
            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            var submittedInvoice = invoiceService.detail(invoiceId);
            assertThat(submittedInvoice.status()).isEqualTo("SUBMITTED");
            assertThatThrownBy(() -> invoiceService.deleteDraft(invoiceId, member,
                    submittedInvoice.version()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.INVOICE_STATE_NOT_ALLOWED));
            var voided = invoiceService.voidFormal(invoiceId, member,
                    new VersionedReasonRequest(submittedInvoice.version(), "发票已冲红"));
            assertThat(voided.status()).isEqualTo("VOIDED");
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
                """, Integer.class, organization.id())).isEqualTo(7);
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
        InvoiceRecognitionAdapter recognitionAdapter() {
            return (file, contentType) -> new InvoiceRecognitionAdapter.RecognitionResult(
                    "山东大学 测试供应商 100.00", "qr-original",
                    Map.of(
                            "sellerName", new InvoiceRecognitionAdapter.SuggestedField(
                                    "测试供应商", new BigDecimal("0.9800")),
                            "faceAmount", new InvoiceRecognitionAdapter.SuggestedField(
                                    "100.00", new BigDecimal("0.9900")),
                            "ignoredField", new InvoiceRecognitionAdapter.SuggestedField(
                                    "ignored", BigDecimal.ONE)), true);
        }
    }
}
