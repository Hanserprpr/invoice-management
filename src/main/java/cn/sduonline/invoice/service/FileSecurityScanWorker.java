package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.FileObject;
import cn.sduonline.invoice.mapper.FileObjectMapper;
import cn.sduonline.invoice.scan.FileContentInspector;
import cn.sduonline.invoice.scan.MalwareScanner;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FileSecurityScanWorker {
    public static final String JOB_TYPE = "FILE_SECURITY_SCAN";

    private final AsyncJobClaimService claimService;
    private final FileObjectMapper fileMapper;
    private final ObjectStorage objectStorage;
    private final FileContentInspector contentInspector;
    private final MalwareScanner malwareScanner;
    private final ObjectMapper objectMapper;

    public FileSecurityScanWorker(AsyncJobClaimService claimService,
                                  FileObjectMapper fileMapper,
                                  ObjectStorage objectStorage,
                                  FileContentInspector contentInspector,
                                  MalwareScanner malwareScanner,
                                  ObjectMapper objectMapper) {
        this.claimService = claimService;
        this.fileMapper = fileMapper;
        this.objectStorage = objectStorage;
        this.contentInspector = contentInspector;
        this.malwareScanner = malwareScanner;
        this.objectMapper = objectMapper;
    }

    public boolean processNext() {
        recoverStaleJobs();
        var claimed = claimService.claim(JOB_TYPE);
        if (claimed.isEmpty()) return false;
        AsyncJob job = claimed.get();
        try (TenantContext.Scope ignored = TenantContext.open(
                job.getOrganizationId(), job.getCreatedByCasId())) {
            process(job);
        } catch (Exception exception) {
            boolean retrying = claimService.retryOrFail(job, errorCode(exception),
                    safeMessage(exception));
            if (!retrying) {
                fileMapper.updateSecurityScanResult(job.getOrganizationId(), job.getTargetId(),
                        "FAILED", null, Instant.now().plusSeconds(86_400));
            }
        }
        return true;
    }

    private void recoverStaleJobs() {
        for (AsyncJob stale : claimService.findStale(
                JOB_TYPE, Instant.now().minus(15, ChronoUnit.MINUTES), 100)) {
            boolean retrying = claimService.retryOrFail(stale, "WORKER_LEASE_EXPIRED",
                    "任务执行节点超时，已重新调度");
            if (!retrying) {
                fileMapper.updateSecurityScanResult(stale.getOrganizationId(), stale.getTargetId(),
                        "FAILED", null, Instant.now().plusSeconds(86_400));
            }
        }
    }

    private void process(AsyncJob job) throws IOException {
        FileObject file = fileMapper.findByOrganizationAndId(
                job.getOrganizationId(), job.getTargetId());
        if (file == null) throw new IllegalStateException("FILE_NOT_FOUND");
        if (!"SCANNING".equals(file.getScanStatus())) {
            claimService.succeed(job.getId(), writeResult(Map.of("outcome", "SKIPPED",
                    "reason", "FILE_STATUS_" + file.getScanStatus())));
            return;
        }
        Path temp = Files.createTempFile("invoice-file-scan-", ".bin");
        try {
            objectStorage.downloadTo(file.getStorageKey(), temp);
            FileContentInspector.Inspection inspection = contentInspector.inspect(temp);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sizeBytes", inspection.sizeBytes());
            result.put("sha256", inspection.sha256());
            result.put("detectedContentType", inspection.contentType());
            if (inspection.sizeBytes() != file.getSizeBytes()
                    || !inspection.sha256().equalsIgnoreCase(file.getSha256())
                    || !inspection.contentType().equalsIgnoreCase(file.getContentType())) {
                result.put("outcome", "REJECTED");
                result.put("reason", "CONTENT_MISMATCH");
                updateFile(file, "REJECTED");
                claimService.succeed(job.getId(), writeResult(result));
                return;
            }
            MalwareScanner.ScanResult malware = malwareScanner.scan(temp);
            result.put("malwareVerdict", malware.verdict().name());
            result.put("malwareDetail", malware.detail());
            switch (malware.verdict()) {
                case CLEAN -> {
                    result.put("outcome", "READY");
                    updateFile(file, "READY");
                }
                case INFECTED -> {
                    result.put("outcome", "REJECTED");
                    result.put("reason", "MALWARE_FOUND");
                    updateFile(file, "REJECTED");
                }
                case UNAVAILABLE -> {
                    result.put("outcome", "MANUAL_REVIEW");
                    result.put("reason", "MALWARE_SCANNER_NOT_CONFIGURED");
                }
            }
            claimService.succeed(job.getId(), writeResult(result));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void updateFile(FileObject file, String status) {
        Instant now = Instant.now();
        Instant readyAt = "READY".equals(status) ? now : null;
        Instant expiresAt = "READY".equals(status) ? null : now.plusSeconds(86_400);
        if (fileMapper.updateSecurityScanResult(file.getOrganizationId(), file.getId(), status,
                readyAt, expiresAt) != 1) {
            throw new IllegalStateException("FILE_STATUS_CONFLICT");
        }
    }

    private String writeResult(Map<String, ?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("RESULT_SERIALIZATION_FAILED", exception);
        }
    }

    private String errorCode(Exception exception) {
        return exception instanceof cn.sduonline.invoice.exception.BusinessException business
                ? business.getBizCode().name() : exception.getClass().getSimpleName();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "任务执行失败" : message;
    }
}
