package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.dto.FormDtos.*;
import cn.sduonline.invoice.data.dto.InvoiceDtos.CreateInvoiceRequest;
import cn.sduonline.invoice.data.dto.InvoiceDtos.UpdateInvoiceRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.*;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.dto.ReviewDtos.*;
import cn.sduonline.invoice.data.dto.RuleDtos.*;
import cn.sduonline.invoice.data.dto.PaperDtos.*;
import cn.sduonline.invoice.data.dto.PrecheckDtos.*;
import cn.sduonline.invoice.data.dto.ExportDtos.*;
import cn.sduonline.invoice.data.dto.ExternalStatusDtos.*;
import cn.sduonline.invoice.data.dto.HandoverDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.*;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.data.vo.ExportBatchVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@org.springframework.context.annotation.Import(ReviewWorkflowIntegrationTests.D4TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class ReviewWorkflowIntegrationTests {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired ProjectService projectService;
    @Autowired ApplicationFormService formService;
    @Autowired ApplicationService applicationService;
    @Autowired FileObjectService fileService;
    @Autowired InvoiceService invoiceService;
    @Autowired ReviewService reviewService;
    @Autowired RuleSetService ruleSetService;
    @Autowired InvoicePrecheckService precheckService;
    @Autowired PaperService paperService;
    @Autowired ExportBatchService exportBatchService;
    @Autowired ExportGenerationWorker exportWorker;
    @Autowired ExternalStatusService externalStatusService;
    @Autowired NotificationService notificationService;
    @Autowired NotificationDeliveryWorker notificationDeliveryWorker;
    @Autowired HandoverService handoverService;
    @Autowired AuthorizationService authorizationService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void reviewerReturnsRestrictedFieldsThenApprovesAndBatchApproves() throws Exception {
        String platform = "review-platform";
        String admin = "review-admin";
        String applicant = "review-member";
        String reviewer = "review-reviewer";
        String scopedReviewer = "review-scoped";
        String ordinary = "review-ordinary";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platform, "平台管理员", "ACTIVE");
        var organization = organizationService.create(platform,
                new CreateOrganizationRequest("审核闭环社团", "CLUB",
                        new InitialAdmin(admin, "社团管理员", null, null)));
        String projectId;
        String formId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(applicant, "申请人", null, null));
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(reviewer, "审核人", null, null));
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(scopedReviewer, "项目审核人", null, null));
            memberService.create(organization.id(), admin,
                    new CreateMemberRequest(ordinary, "普通成员", null, null));
            var ruleSet = ruleSetService.create(admin,
                    new CreateRuleSetRequest("审核测试规则", true));
            var ruleVersion = ruleSetService.publish(ruleSet.id(), admin,
                    new PublishRuleVersionRequest(List.of(new RuleDefinition(
                            "REQUIRE_SELLER_TAX_NO", "WARNING", null, null)),
                            Instant.now().minusSeconds(1)));
            var project = projectService.create(admin, new CreateProjectRequest(
                    "审核测试项目", null, null, null, true, ruleVersion.id(), "ALL", null, null,
                    List.of(admin), List.of()));
            projectId = project.id();
            memberService.replaceRoles(organization.id(), reviewer, admin,
                    new ReplaceRolesRequest(0L,
                            List.of(new RoleAssignment("MEMBER", null, null),
                                    new RoleAssignment("REVIEWER", null, null)), List.of()));
            memberService.replaceRoles(organization.id(), scopedReviewer, admin,
                    new ReplaceRolesRequest(0L,
                            List.of(new RoleAssignment("MEMBER", null, null)),
                            List.of(new ProjectGrant(projectId, Set.of("REVIEW")))));
            var form = formService.create(projectId, admin, new CreateFormRequest(
                    "审核申请", "ALL_MEMBERS", null, null, 1,
                    new FormSchema(List.of(new FormField("invoices", "INVOICE", "发票", true,
                            null, null, null, null, null, null, null)))));
            formService.publish(form.id(), admin, new FormVersionRequest(form.version()));
            projectService.open(projectId, admin, new ChangeStateRequest(project.version()));
            formId = form.id();
        }

        String fileOne;
        String fileTwo;
        String applicationId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), applicant)) {
            applicationId = applicationService.createDraft(formId, applicant).id();
            fileOne = register(applicant, "review-one.pdf", "d");
            fileTwo = register(applicant, "review-two.pdf", "e");
        }
        fileService.inspect(platform, organization.id(), fileOne, new InspectFileRequest("READY", null));
        fileService.inspect(platform, organization.id(), fileTwo, new InspectFileRequest("READY", null));

        String invoiceOne;
        String invoiceTwo;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), applicant)) {
            invoiceOne = invoiceService.create(applicationId, applicant,
                    invoiceRequest(fileOne, "100.00", "80.00", "R-001")).id();
            invoiceTwo = invoiceService.create(applicationId, applicant,
                    invoiceRequest(fileTwo, "200.00", "150.00", "R-002")).id();
            var saved = applicationService.saveDraft(applicationId, applicant,
                    new SaveDraftRequest(objectMapper.readTree("{\"invoices\":[\"" + invoiceOne
                            + "\",\"" + invoiceTwo + "\"]}"), 0L));
            applicationService.submit(applicationId, applicant, new SubmitRequest(saved.version()));
        }

        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), ordinary)) {
            assertThat(reviewService.queue(1, 20, null, null, null).records()).isEmpty();
            assertThatThrownBy(() -> reviewService.detail(invoiceOne))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.NO_PERMISSION));
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), scopedReviewer)) {
            assertThat(reviewService.queue(1, 20, projectId, "SUBMITTED", null).records())
                    .extracting("invoiceId").containsExactlyInAnyOrder(invoiceOne, invoiceTwo);
        }

        String returnReasonId = dictionaryItem(organization.id(), "RETURN_REASON", "AMOUNT_MISMATCH");
        String categoryId = dictionaryItem(organization.id(), "EXPENSE_CATEGORY", "TRANSPORT");
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            assertThatThrownBy(() -> reviewService.approve(invoiceOne, reviewer,
                    new ApproveReviewRequest(1L, null, null, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.REVIEW_NOT_STARTED));
            var started = reviewService.start(invoiceOne, reviewer, new StartReviewRequest(1L));
            assertThat(started.invoice().status()).isEqualTo("IN_REVIEW");
            assertThat(applicationStatus(applicationId)).isEqualTo("PROCESSING");
            var returned = reviewService.returnForCorrection(invoiceOne, reviewer,
                    new ReturnReviewRequest(2L, returnReasonId, "请核对申请金额",
                            Set.of("claimedAmount")));
            assertThat(returned.invoice().status()).isEqualTo("RETURNED");
            assertThat(returned.reviews()).extracting("action")
                    .containsExactly("START_REVIEW", "RETURN");
            assertThat(applicationStatus(applicationId)).isEqualTo("RETURNED");
        }

        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), applicant)) {
            assertThatThrownBy(() -> invoiceService.update(invoiceOne, applicant,
                    new UpdateInvoiceRequest(3L, null, null, null, null, null,
                            null, null, "不允许的销售方", null,
                            null, null, null, Set.of())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.REVIEW_FIELD_NOT_ALLOWED));
            var corrected = invoiceService.update(invoiceOne, applicant,
                    new UpdateInvoiceRequest(3L, null, null, null, null, null,
                            null, null, null, null, null, new BigDecimal("70.00"),
                            null, Set.of()));
            assertThat(corrected.claimedAmount()).isEqualByComparingTo("70.00");
            var application = applicationService.detail(applicationId);
            assertThatThrownBy(() -> applicationService.saveDraft(applicationId, applicant,
                    new SaveDraftRequest(objectMapper.readTree("{\"invoices\":[]}"), application.version())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.REVIEW_ANSWER_IMMUTABLE));
            applicationService.submit(applicationId, applicant, new SubmitRequest(application.version()));
        }

        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            reviewService.start(invoiceOne, reviewer, new StartReviewRequest(5L));
            var approved = reviewService.approve(invoiceOne, reviewer,
                    new ApproveReviewRequest(6L, categoryId, "已复核金额", null));
            assertThat(approved.invoice().status()).isEqualTo("INTERNALLY_APPROVED");
            assertThat(approved.internalNote()).isEqualTo("已复核金额");
            assertThat(applicationStatus(applicationId)).isEqualTo("PROCESSING");
            assertThat(precheckService.list(invoiceOne))
                    .anySatisfy(result -> {
                        assertThat(result.ruleCode()).isEqualTo("REQUIRE_SELLER_TAX_NO");
                        assertThat(result.result()).isEqualTo("HIT");
                        assertThat(result.severity()).isEqualTo("WARNING");
                    });
            assertThat(paperService.detail(invoiceOne).status()).isEqualTo("PENDING_DELIVERY");

            var batch = reviewService.batchApprove(reviewer,
                    new BatchApproveRequest(List.of(new BatchApproveItem(invoiceTwo, 1L))));
            assertThat(batch.getFirst().invoice().status()).isEqualTo("INTERNALLY_APPROVED");
            assertThat(batch.getFirst().reviews()).extracting("action")
                    .containsExactly("START_REVIEW", "APPROVE");
            assertThat(batch.getFirst().reviews().getFirst().batchOperationId())
                    .isEqualTo(batch.getFirst().reviews().getLast().batchOperationId());
            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            assertThatThrownBy(() -> reviewService.start(invoiceOne, reviewer,
                    new StartReviewRequest(4L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode()).isEqualTo(BizCode.VERSION_CONFLICT));
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), applicant)) {
            assertThat(reviewService.history(invoiceOne)).extracting("action")
                    .containsExactly("START_REVIEW", "RETURN", "START_REVIEW", "APPROVE");
            assertThat(paperService.declare(invoiceOne, applicant,
                    new PaperVersionRequest(0L)).status()).isEqualTo("MEMBER_DECLARED");
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            var scan = paperService.scan(projectId, reviewer,
                    new PaperScanRequest("invoiceCode=3700&invoiceNumber=R-001"));
            assertThat(scan.result()).isEqualTo("SUCCESS");
            assertThat(scan.paperItem().status()).isEqualTo("CLUB_RECEIVED");
            assertThat(paperService.scan(projectId, reviewer,
                    new PaperScanRequest("invoiceCode=3700&invoiceNumber=R-001")).result())
                    .isEqualTo("ALREADY_SCANNED");

            String duplicateId = UlidGenerator.next();
            jdbcTemplate.update("""
                    INSERT INTO invoice(id,organization_id,application_id,invoice_type,
                      invoice_code,invoice_number,invoice_date,seller_name,face_amount,
                      claimed_amount,current_file_id,status,version)
                    SELECT ?,organization_id,application_id,invoice_type,invoice_code,
                      invoice_number,invoice_date,seller_name,face_amount,claimed_amount,
                      current_file_id,'SUBMITTED',0 FROM invoice WHERE id=?
                    """, duplicateId, invoiceOne);
            var duplicateResults = precheckService.run(duplicateId, reviewer);
            var exact = duplicateResults.stream()
                    .filter(result -> "EXACT_DUPLICATE".equals(result.checkType()))
                    .findFirst().orElseThrow();
            assertThat(exact.result()).isEqualTo("HIT");
            assertThat(exact.reason()).doesNotContain(invoiceOne);
            precheckService.resolve(duplicateId, exact.id(), reviewer,
                    new ResolvePrecheckRequest("FALSE_POSITIVE", "已核对为测试票据"));
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM invoice_precheck_result
                    WHERE invoice_id=? AND severity='BLOCK' AND result='HIT' AND resolution IS NULL
                    """, Integer.class, duplicateId)).isZero();
            jdbcTemplate.update("DELETE FROM invoice_precheck_result WHERE invoice_id=?", duplicateId);
            jdbcTemplate.update("DELETE FROM invoice WHERE id=?", duplicateId);

            var cancellable = exportBatchService.create(reviewer,
                    new CreateExportBatchRequest("REVIEW-CANCELLED", List.of(invoiceOne, invoiceTwo)));
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(reservationBatch(invoiceOne)).isEqualTo(cancellable.id());
            assertThat(reservationBatch(invoiceTwo)).isEqualTo(cancellable.id());
            exportBatchService.cancel(cancellable.id(), reviewer,
                    new BatchVersionRequest(cancellable.version()));
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("INTERNALLY_APPROVED");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("INTERNALLY_APPROVED");
            assertThat(reservationBatch(invoiceOne)).isNull();
            assertThat(reservationBatch(invoiceTwo)).isNull();

            var export = exportBatchService.create(reviewer,
                    new CreateExportBatchRequest("REVIEW-2026", List.of(invoiceOne, invoiceTwo)));
            assertThat(export.invoiceCount()).isEqualTo(2);
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("IN_EXPORT_BATCH");
            assertThatThrownBy(() -> exportBatchService.create(reviewer,
                    new CreateExportBatchRequest("DUPLICATE", List.of(invoiceOne))))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.EXPORT_INVOICE_RESERVED));
            exportBatchService.generate(export.id(), reviewer, new BatchVersionRequest(0L));
            assertThat(exportWorker.processNext()).isTrue();
            ExportBatchVO generated = exportBatchService.detail(export.id());
            assertThat(generated.status())
                    .withFailMessage("generation job: %s", generated.generationJob())
                    .isEqualTo("GENERATED");
            assertThat(generated.artifacts()).hasSize(4)
                    .extracting(ExportBatchVO.ArtifactVO::artifactType)
                    .containsExactlyInAnyOrder("LEDGER_XLSX", "LIST_PDF", "ATTACHMENT_ZIP", "MANIFEST_JSON");
            ExportBatchVO revised = exportBatchService.revise(export.id(), reviewer,
                    new BatchVersionRequest(generated.version()));
            assertThat(revised.revisionNo()).isEqualTo(2);
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(reservationBatch(invoiceOne)).isEqualTo(revised.id());
            assertThat(reservationBatch(invoiceTwo)).isEqualTo(revised.id());
            exportBatchService.generate(revised.id(), reviewer, new BatchVersionRequest(0L));
            assertThat(exportWorker.processNext()).isTrue();
            ExportBatchVO regenerated = exportBatchService.detail(revised.id());
            assertThat(regenerated.artifacts()).hasSize(4);
            exportBatchService.download(revised.id(), regenerated.artifacts().getFirst().id(), reviewer);
        }
        String activeBatchId = jdbcTemplate.queryForObject("""
                SELECT id FROM export_batch WHERE organization_id=? AND batch_no='REVIEW-2026'
                  AND revision_no=2
                """, String.class, organization.id());
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            var comment = externalStatusService.append(activeBatchId, admin,
                    new CreateExternalEventRequest("COMMENT", "RETURNED_EXTERNAL",
                            "仅追加备注，不改变批次状态", List.of(), 2L));
            assertThat(comment.status()).isEqualTo("RETURNED_EXTERNAL");
            assertThat(exportBatchService.detail(activeBatchId).status()).isEqualTo("EXPORTED");
            assertThat(exportBatchService.detail(activeBatchId).version()).isEqualTo(2L);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notification
                    WHERE notification_type='EXTERNAL_BATCH_RETURNED' AND target_id=?
                    """, Integer.class, activeBatchId)).isZero();
            assertThatThrownBy(() -> externalStatusService.correct(activeBatchId, comment.id(), admin,
                    new CorrectExternalEventRequest("SUBMITTED_EXTERNAL", "备注不能更正为状态",
                            List.of(), 2L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.EXTERNAL_STATUS_CORRECTION_INVALID));
            assertThat(exportBatchService.detail(activeBatchId).status()).isEqualTo("EXPORTED");
            assertThat(exportBatchService.detail(activeBatchId).version()).isEqualTo(2L);
            assertThatThrownBy(() -> externalStatusService.append(activeBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "ARCHIVED",
                            "不能跳过完成直接归档", List.of(), 2L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.EXPORT_BATCH_STATE_NOT_ALLOWED));

            externalStatusService.append(activeBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "SUBMITTED_EXTERNAL",
                            "已提交校外流程", List.of(), 2L));
            externalStatusService.append(activeBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "CANCELLED",
                            "校外流程取消", List.of(), 3L));
            assertThat(exportBatchService.detail(activeBatchId).status()).isEqualTo("CANCELLED");
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("INTERNALLY_APPROVED");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("INTERNALLY_APPROVED");
            assertThat(reservationBatch(invoiceOne)).isNull();
            assertThat(reservationBatch(invoiceTwo)).isNull();
        }

        String finalBatchId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            var finalBatch = exportBatchService.create(reviewer,
                    new CreateExportBatchRequest("REVIEW-FINAL", List.of(invoiceOne, invoiceTwo)));
            finalBatchId = finalBatch.id();
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            // 草稿批次尚未产出任何材料，不应存在“平台外状态”事件
            assertThatThrownBy(() -> externalStatusService.append(finalBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "CANCELLED",
                            "草稿批次不能走平台外流程", List.of(), 0L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getBizCode())
                                    .isEqualTo(BizCode.EXPORT_BATCH_STATE_NOT_ALLOWED));
            assertThat(exportBatchService.detail(finalBatchId).status()).isEqualTo("DRAFT");
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            exportBatchService.generate(finalBatchId, reviewer, new BatchVersionRequest(0L));
            assertThat(exportWorker.processNext()).isTrue();
            ExportBatchVO finalGenerated = exportBatchService.detail(finalBatchId);
            exportBatchService.download(finalBatchId, finalGenerated.artifacts().getFirst().id(), reviewer);
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), admin)) {
            var submitted = externalStatusService.append(finalBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "SUBMITTED_EXTERNAL",
                            "已重新提交校外流程", List.of(), 2L));
            var corrected = externalStatusService.correct(finalBatchId, submitted.id(), admin,
                    new CorrectExternalEventRequest("RETURNED_EXTERNAL", "对方实际退回",
                            List.of(), 3L));
            assertThat(corrected.correctionOfEventId()).isEqualTo(submitted.id());
            externalStatusService.append(finalBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "COMPLETED",
                            "校外流程完成", List.of(), 4L));
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("IN_EXPORT_BATCH");
            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            externalStatusService.append(finalBatchId, admin,
                    new CreateExternalEventRequest("STATUS_CHANGE", "ARCHIVED",
                            "校外流程归档", List.of(), 5L));
            assertThat(exportBatchService.detail(finalBatchId).status()).isEqualTo("ARCHIVED");
            assertThat(invoiceStatus(invoiceOne)).isEqualTo("ARCHIVED");
            assertThat(invoiceStatus(invoiceTwo)).isEqualTo("ARCHIVED");
            assertThat(applicationStatus(applicationId)).isEqualTo("COMPLETED");
            var archivedCorrection = externalStatusService.correct(
                    finalBatchId, submitted.id(), admin,
                    new CorrectExternalEventRequest("COMPLETED", "归档后补充更正说明",
                            List.of(), 6L));
            assertThat(archivedCorrection.correctionOfEventId()).isEqualTo(submitted.id());
            assertThat(exportBatchService.detail(finalBatchId).status()).isEqualTo("ARCHIVED");
            assertThat(exportBatchService.detail(finalBatchId).version()).isEqualTo(6L);

            long adminVersion = jdbcTemplate.queryForObject("""
                    SELECT version FROM organization_member WHERE organization_id=? AND cas_id=?
                    """, Long.class, organization.id(), admin);
            var handover = handoverService.create(admin,
                    new CreateHandoverRequest(admin, ordinary, adminVersion, "年度换届"));
            assertThat(handover.transferredProjectIds()).contains(projectId);
        }
        while (notificationDeliveryWorker.processNext()) { }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM async_job WHERE organization_id=?
                  AND job_type='NOTIFICATION_DELIVERY' AND status='SUCCEEDED'
                """, Integer.class, organization.id())).isEqualTo(2);
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), reviewer)) {
            assertThat(notificationService.inbox(1, 20, "UNREAD").records())
                    .anySatisfy(notification -> assertThat(notification.notificationType())
                            .isEqualTo("EXTERNAL_BATCH_RETURNED"));
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), ordinary)) {
            assertThat(authorizationService.canManageProject(projectId)).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE organization_id=?
                  AND action LIKE 'INVOICE_REVIEW_%'
                """, Integer.class, organization.id())).isEqualTo(5);
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

    private String applicationStatus(String applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM application WHERE id=?",
                String.class, applicationId);
    }

    private String invoiceStatus(String invoiceId) {
        return jdbcTemplate.queryForObject("SELECT status FROM invoice WHERE id=?",
                String.class, invoiceId);
    }

    private String reservationBatch(String invoiceId) {
        return jdbcTemplate.query("SELECT batch_id FROM invoice_export_reservation WHERE invoice_id=?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null, invoiceId);
    }

    private CreateInvoiceRequest invoiceRequest(String fileId, String face, String claimed,
                                                 String invoiceNumber) {
        return new CreateInvoiceRequest("VAT_ELECTRONIC", "3700", invoiceNumber, null,
                LocalDate.of(2026, 8, 1), "山东大学", null, "测试供应商", null,
                new BigDecimal(face), new BigDecimal(claimed), fileId, null);
    }

    @TestConfiguration
    static class D4TestConfiguration {
        @Bean
        @Primary
        ObjectStorage d4ObjectStorage() {
            return new ObjectStorage() {
                private final Map<String, byte[]> objects = new java.util.concurrent.ConcurrentHashMap<>();

                @Override
                public UploadGrant createUploadGrant(String key, String contentType, long sizeBytes, String sha256) {
                    return new UploadGrant("https://upload.test/" + key, Map.of(), Instant.now().plusSeconds(60));
                }

                @Override
                public Optional<StoredObject> headUpload(String key) { return Optional.empty(); }

                @Override
                public void finalizeUpload(String key) { }

                @Override
                public void downloadTo(String key, Path target) {
                    try {
                        Files.write(target, objects.getOrDefault(key,
                                "%PDF-1.4\n% test invoice\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }

                @Override
                public void uploadFrom(String key, Path source, String contentType, String sha256) {
                    try { objects.put(key, Files.readAllBytes(source)); }
                    catch (IOException exception) { throw new IllegalStateException(exception); }
                }

                @Override
                public void delete(String key) { objects.remove(key); }

                @Override
                public DownloadGrant createDownloadGrant(String key, String originalName, String contentType) {
                    if (!objects.containsKey(key)) throw new IllegalStateException("missing object");
                    return new DownloadGrant("https://download.test/" + key, Instant.now().plusSeconds(60));
                }
            };
        }
    }
}
