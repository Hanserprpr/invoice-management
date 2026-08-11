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
import cn.sduonline.invoice.service.MemberService;
import cn.sduonline.invoice.service.OrganizationService;
import cn.sduonline.invoice.storage.ObjectStorage;
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

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    }

    @Test
    @Transactional
    void presignedUploadCompletionAndPrivateDownloadFormAClosedLoop() {
        String platform = "storage-platform";
        String uploader = "storage-uploader";
        String other = "storage-other";
        jdbcTemplate.update("INSERT INTO `user`(cas_id,name,status,is_platform_admin) VALUES (?,?,?,TRUE)",
                platform, "存储平台管理员", "ACTIVE");
        var organization = organizationService.create(platform,
                new CreateOrganizationRequest("对象存储测试社团", "CLUB",
                        new InitialAdmin(uploader, "上传者", null, null)));

        String fileId;
        try (TenantContext.Scope ignored = TenantContext.open(organization.id(), uploader)) {
            memberService.create(organization.id(), uploader,
                    new CreateMemberRequest(other, "其他成员", null, null));
            var upload = fileService.createUpload(uploader, new RegisterFileRequest(
                    "invoice.pdf", "application/pdf", 128L, "a".repeat(64),
                    "INVOICE_ORIGINAL"));
            fileId = upload.file().id();
            assertThat(upload.method()).isEqualTo("PUT");
            assertThat(upload.uploadUrl()).startsWith("https://r2.test/upload/");
            assertThat(upload.requiredHeaders()).containsKeys("Content-Type", "x-amz-meta-sha256");
            assertThat(fileService.completeUpload(uploader, fileId,
                    new CompleteUploadRequest("a".repeat(64))).scanStatus()).isEqualTo("SCANNING");
        }

        fileService.inspect(platform, organization.id(), fileId,
                new InspectFileRequest("READY", null));
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
    }

    static class FakeObjectStorage implements ObjectStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

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
        public DownloadGrant createDownloadGrant(String key, String originalName,
                                                  String contentType) {
            return new DownloadGrant("https://r2.test/download/" + key.substring(key.lastIndexOf('/') + 1),
                    Instant.now().plusSeconds(300));
        }
    }
}
