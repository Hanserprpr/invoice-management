package cn.sduonline.invoice;

import cn.sduonline.invoice.data.dto.FileDtos.CompleteUploadRequest;
import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.InitialAdmin;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.FileObjectService;
import cn.sduonline.invoice.service.FileSecurityScanWorker;
import cn.sduonline.invoice.service.FileOrphanDeletionService;
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.scan.MalwareScanner;
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

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret"
})
@Import(FileStorageIntegrationTests.FakeStorageConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class FileStorageIntegrationTests {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired OrganizationService organizationService;
    @Autowired MemberService memberService;
    @Autowired FileObjectService fileService;
    @Autowired FileSecurityScanWorker scanWorker;
    @Autowired FakeObjectStorage fakeObjectStorage;
    @Autowired FakeMalwareScanner fakeMalwareScanner;
    @Autowired FileOrphanDeletionService orphanDeletionService;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void presignedUploadCompletionAndPrivateDownloadFormAClosedLoop() throws Exception {
        String platform = "storage-platform";
        String uploader = "storage-uploader";
        String other = "storage-other";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platform, "存储平台管理员", "ACTIVE");
        var organization = organizationService.create(platform,
                new CreateOrganizationRequest("对象存储测试社团", "CLUB",
                        new InitialAdmin(uploader, "上传者", null, null)));

        String fileId;
        byte[] pdf = "%PDF-1.7\nbody\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            memberService.create(organization.id(), uploader,
                    new CreateMemberRequest(other, "其他成员", null, null));
            var upload = fileService.createUpload(uploader, new RegisterFileRequest(
                    "invoice.pdf", "application/pdf", (long) pdf.length, sha256,
                    "INVOICE_ORIGINAL"));
            fileId = upload.file().id();
            assertThat(upload.method()).isEqualTo("PUT");
            assertThat(upload.uploadUrl()).startsWith("https://r2.test/upload/");
            assertThat(upload.requiredHeaders()).containsKeys("Content-Type", "x-amz-meta-sha256");
            assertThat(fileService.completeUpload(uploader, fileId,
                    new CompleteUploadRequest(sha256)).scanStatus()).isEqualTo("SCANNING");
        }

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM file_object WHERE id=?", String.class, fileId);
        fakeObjectStorage.putBody(storageKey, pdf);
        assertThat(scanWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_status FROM file_object WHERE id=?", String.class, fileId))
                .isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result_json FROM async_job WHERE target_id=?", String.class, fileId))
                .contains("\"outcome\": \"READY\"")
                .contains("\"malwareVerdict\": \"CLEAN\"");

        fakeMalwareScanner.verdict = MalwareScanner.Verdict.UNAVAILABLE;
        byte[] secondPdf = "%PDF-1.7\nmanual-review\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String secondHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(secondPdf));
        String manualFileId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            manualFileId = fileService.createUpload(uploader, new RegisterFileRequest(
                    "manual.pdf", "application/pdf", (long) secondPdf.length, secondHash,
                    "INVOICE_ORIGINAL")).file().id();
            fileService.completeUpload(uploader, manualFileId, new CompleteUploadRequest(secondHash));
        }
        String manualKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM file_object WHERE id=?", String.class, manualFileId);
        fakeObjectStorage.putBody(manualKey, secondPdf);
        assertThat(scanWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_status FROM file_object WHERE id=?", String.class, manualFileId))
                .isEqualTo("SCANNING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result_json FROM async_job WHERE target_id=?", String.class, manualFileId))
                .contains("\"outcome\": \"MANUAL_REVIEW\"");
        assertThat(fileService.inspect(platform, organization.id(), manualFileId,
                new InspectFileRequest("READY", null)).scanStatus()).isEqualTo("READY");

        byte[] orphanBody = "%PDF-1.7\norphan\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String orphanHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(orphanBody));
        String orphanId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            orphanId = fileService.createUpload(uploader, new RegisterFileRequest(
                    "orphan.pdf", "application/pdf", (long) orphanBody.length, orphanHash,
                    "OTHER")).file().id();
        }
        String orphanKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM file_object WHERE id=?", String.class, orphanId);
        jdbcTemplate.update("UPDATE file_object SET scan_status='FAILED',expires_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",
                orphanId);
        assertThat(fakeObjectStorage.contains(orphanKey + ".upload")).isTrue();
        assertThat(orphanDeletionService.deleteIfStillExpired(organization.id(), orphanId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_object WHERE id=?", Integer.class, orphanId)).isZero();
        assertThat(fakeObjectStorage.contains(orphanKey + ".upload")).isFalse();

        byte[] retryBody = "%PDF-1.7\nretry\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        String retryHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(retryBody));
        String retryFileId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            retryFileId = fileService.createUpload(uploader, new RegisterFileRequest(
                    "retry.pdf", "application/pdf", (long) retryBody.length, retryHash,
                    "OTHER")).file().id();
            fileService.completeUpload(uploader, retryFileId, new CompleteUploadRequest(retryHash));
        }
        fakeObjectStorage.failDownloads = true;
        assertThat(scanWorker.processNext()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(status,':',attempt_count,':',next_attempt_at IS NOT NULL) FROM async_job WHERE target_id=?",
                String.class, retryFileId)).isEqualTo("PENDING:1:1");
        for (int attempt = 2; attempt <= 3; attempt++) {
            jdbcTemplate.update("UPDATE async_job SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE target_id=?",
                    retryFileId);
            assertThat(scanWorker.processNext()).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(status,':',attempt_count) FROM async_job WHERE target_id=?",
                String.class, retryFileId)).isEqualTo("FAILED:3");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_status FROM file_object WHERE id=?", String.class, retryFileId))
                .isEqualTo("FAILED");
        fakeObjectStorage.failDownloads = false;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            assertThat(fileService.createDownload(fileId).downloadUrl())
                    .isEqualTo("https://r2.test/download/" + fileId);
        }
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), other)) {
            assertThatThrownBy(() -> fileService.createDownload(fileId))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getBizCode()).isEqualTo(BizCode.FILE_NOT_FOUND));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        FakeObjectStorage fakeObjectStorage() {
            return new FakeObjectStorage();
        }

        @Bean
        @Primary
        FakeMalwareScanner fakeMalwareScanner() {
            return new FakeMalwareScanner();
        }
    }

    static class FakeMalwareScanner implements MalwareScanner {
        private Verdict verdict = Verdict.CLEAN;

        @Override
        public ScanResult scan(Path path) {
            return new ScanResult(verdict, "TEST_" + verdict.name());
        }
    }

    static class FakeObjectStorage implements ObjectStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private final Map<String, byte[]> bodies = new ConcurrentHashMap<>();
        private boolean failDownloads;

        @Override
        public UploadGrant createUploadGrant(String key, String contentType, long sizeBytes,
                                             String sha256) {
            objects.put(key + ".upload",
                    new StoredObject(sizeBytes, contentType, Map.of("sha256", sha256)));
            return new UploadGrant("https://r2.test/upload/" + key,
                    Map.of("Content-Type", List.of(contentType),
                            "x-amz-meta-sha256", List.of(sha256)),
                    Instant.now().plusSeconds(600));
        }

        @Override
        public Optional<StoredObject> headUpload(String key) {
            StoredObject pending = objects.get(key + ".upload");
            return Optional.ofNullable(pending == null ? objects.get(key) : pending);
        }

        @Override
        public void finalizeUpload(String key) {
            StoredObject pending = objects.remove(key + ".upload");
            if (pending == null) throw new IllegalStateException("missing upload");
            objects.put(key, pending);
        }

        @Override
        public void downloadTo(String key, Path target) {
            if (failDownloads) throw new IllegalStateException("simulated storage failure");
            byte[] body = bodies.get(key);
            if (body == null) throw new IllegalStateException("missing body");
            try {
                Files.write(target, body);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
            objects.remove(key + ".upload");
            bodies.remove(key);
        }

        void putBody(String key, byte[] body) {
            bodies.put(key, body.clone());
        }

        boolean contains(String key) {
            return objects.containsKey(key);
        }

        @Override
        public DownloadGrant createDownloadGrant(String key, String originalName,
                                                  String contentType) {
            return new DownloadGrant("https://r2.test/download/" + key.substring(key.lastIndexOf('/') + 1),
                    Instant.now().plusSeconds(300));
        }
    }
}
