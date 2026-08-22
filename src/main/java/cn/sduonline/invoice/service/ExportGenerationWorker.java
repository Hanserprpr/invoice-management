package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.ExportBatch;
import cn.sduonline.invoice.data.po.ExportBatchInvoice;
import cn.sduonline.invoice.mapper.ExportBatchInvoiceMapper;
import cn.sduonline.invoice.mapper.ExportBatchMapper;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportGenerationWorker {
    private static final String JOB_TYPE = "EXPORT_GENERATION";
    private static final List<String> ARTIFACT_FILE_NAMES = List.of(
            "invoice-ledger.xlsx", "invoice-list.pdf", "attachments.zip", "manifest.json");

    private final AsyncJobClaimService claimService;
    private final ExportBatchMapper batchMapper;
    private final ExportBatchInvoiceMapper itemMapper;
    private final ObjectStorage storage;
    private final ExportArtifactPersistenceService persistenceService;
    private final cn.sduonline.invoice.mapper.ExportArtifactMapper artifactMapper;
    private final ObjectMapper objectMapper;

    public ExportGenerationWorker(AsyncJobClaimService claimService, ExportBatchMapper batchMapper,
                                  ExportBatchInvoiceMapper itemMapper, ObjectStorage storage,
                                  ExportArtifactPersistenceService persistenceService,
                                  cn.sduonline.invoice.mapper.ExportArtifactMapper artifactMapper,
                                  ObjectMapper objectMapper) {
        this.claimService = claimService;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.storage = storage;
        this.persistenceService = persistenceService;
        this.artifactMapper = artifactMapper;
        this.objectMapper = objectMapper;
    }

    public boolean processNext() {
        for (AsyncJob stale : claimService.findStale(JOB_TYPE,
                Instant.now().minus(15, ChronoUnit.MINUTES), 20)) {
            claimService.retryOrFail(stale, "WORKER_LEASE_EXPIRED", "导出节点超时，已重新调度");
        }
        Optional<AsyncJob> claimed = claimService.claim(JOB_TYPE);
        if (claimed.isEmpty()) return false;
        AsyncJob job = claimed.get();
        try {
            TenantContext.TenantInfo current = TenantContext.getNullable();
            if (current == null) {
                try (TenantContext.Scope ignored = TenantContext.open(job.getOrganizationId(),
                        job.getCreatedByCasId())) {
                    generate(job);
                }
            } else if (job.getOrganizationId().equals(current.organizationId())) {
                generate(job);
            } else {
                throw new IllegalStateException("工作线程租户上下文不匹配");
            }
        } catch (Exception exception) {
            claimService.retryOrFail(job, "EXPORT_GENERATION_FAILED", safeMessage(exception));
        }
        return true;
    }

    private void generate(AsyncJob job) throws IOException {
        ExportBatch batch = batchMapper.findOne(job.getOrganizationId(), job.getTargetId());
        if (batch != null && "GENERATED".equals(batch.getStatus())
                && artifactMapper.findForBatch(job.getOrganizationId(), batch.getId()).size() == 4) {
            claimService.succeed(job, "{\"outcome\":\"ALREADY_GENERATED\",\"artifactCount\":4}");
            return;
        }
        if (batch == null || !"DRAFT".equals(batch.getStatus())) {
            throw new IllegalStateException("导出批次不存在或状态已变化");
        }
        List<ExportBatchInvoice> items = itemMapper.findForBatch(job.getOrganizationId(), batch.getId());
        if (items.isEmpty()) throw new IllegalStateException("导出批次没有发票");
        discardAbandonedAttempts(job);
        Path directory = Files.createTempDirectory("invoice-export-");
        List<ExportArtifactPersistenceService.GeneratedArtifact> generated = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();
        boolean persisted = false;
        try {
            Path xlsx = directory.resolve("invoice-ledger.xlsx");
            writeWorkbook(xlsx, items);
            Path pdf = directory.resolve("invoice-list.pdf");
            writePdf(pdf, batch, items);
            Path zip = directory.resolve("attachments.zip");
            writeZip(zip, job.getOrganizationId(), batch.getId(), directory);
            generated.add(upload(job, xlsx, "LEDGER_XLSX",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", uploadedKeys));
            generated.add(upload(job, pdf, "LIST_PDF", "application/pdf", uploadedKeys));
            generated.add(upload(job, zip, "ATTACHMENT_ZIP", "application/zip", uploadedKeys));
            Path manifest = directory.resolve("manifest.json");
            writeManifest(manifest, batch, generated);
            generated.add(upload(job, manifest, "MANIFEST_JSON", "application/json", uploadedKeys));
            persistenceService.persist(job, batch.getId(), generated);
            persisted = true;
            claimService.succeed(job, objectMapper.writeValueAsString(Map.of(
                    "outcome", "GENERATED", "artifactCount", generated.size())));
        } catch (Exception exception) {
            if (!persisted) {
                uploadedKeys.forEach(key -> {
                    try { storage.delete(key); } catch (RuntimeException ignored) { }
                });
            }
            throw exception;
        } finally {
            deleteDirectory(directory);
        }
    }

    private ExportArtifactPersistenceService.GeneratedArtifact upload(
            AsyncJob job, Path source, String type, String contentType, List<String> uploadedKeys)
            throws IOException {
        String fileId = UlidGenerator.next();
        String fileName = source.getFileName().toString();
        String key = attemptPrefix(job, job.getLeaseVersion()) + fileName;
        String hash = sha256(source);
        storage.uploadFrom(key, source, contentType, hash);
        uploadedKeys.add(key);
        return new ExportArtifactPersistenceService.GeneratedArtifact(fileId, key, fileName,
                fileName, type, contentType, Files.size(source), hash);
    }

    private void writeWorkbook(Path target, List<ExportBatchInvoice> items) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet("Invoices");
            String[] headers = {"Sequence", "Invoice ID", "Face Amount", "Claimed Amount"};
            Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
            }
            for (int index = 0; index < items.size(); index++) {
                ExportBatchInvoice item = items.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(item.getSequenceNo());
                row.createCell(1).setCellValue(item.getInvoiceId());
                row.createCell(2).setCellValue(item.getSnapshotFaceAmount().doubleValue());
                row.createCell(3).setCellValue(item.getSnapshotClaimedAmount().doubleValue());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
        }
    }

    private void writePdf(Path target, ExportBatch batch, List<ExportBatchInvoice> items)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            int offset = 0;
            while (offset < items.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.newLineAtOffset(45, 800);
                    content.showText("Invoice export: " + batch.getBatchNo()
                            + " revision " + batch.getRevisionNo());
                    content.newLineAtOffset(0, -22);
                    for (int count = 0; count < 38 && offset < items.size(); count++, offset++) {
                        ExportBatchInvoice item = items.get(offset);
                        content.showText(item.getSequenceNo() + "  " + item.getInvoiceId()
                                + "  face=" + item.getSnapshotFaceAmount()
                                + "  claimed=" + item.getSnapshotClaimedAmount());
                        content.newLineAtOffset(0, -18);
                    }
                    content.endText();
                }
            }
            document.save(target.toFile());
        }
    }

    private void writeZip(Path target, String organizationId, String batchId, Path directory)
            throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target),
                StandardCharsets.UTF_8)) {
            Set<String> entries = new HashSet<>();
            for (ExportBatchInvoiceMapper.SourceFile source : itemMapper
                    .findSourceFiles(organizationId, batchId)) {
                String safeName = source.originalName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                        .replace("..", "__");
                if (safeName.isBlank() || ".".equals(safeName)) safeName = source.fileId();
                String entryName = source.invoiceId() + "/" + safeName;
                if (!entries.add(entryName)) entryName = source.invoiceId() + "/" + source.fileId() + "-" + safeName;
                Path downloaded = directory.resolve(source.fileId());
                storage.downloadTo(source.storageKey(), downloaded);
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(downloaded, zip);
                zip.closeEntry();
                Files.deleteIfExists(downloaded);
            }
        }
    }

    private void writeManifest(Path target, ExportBatch batch,
                               List<ExportArtifactPersistenceService.GeneratedArtifact> generated)
            throws IOException {
        List<Map<String, Object>> files = generated.stream().map(item -> Map.<String, Object>of(
                "path", item.relativePath(), "type", item.artifactType(),
                "sizeBytes", item.sizeBytes(), "sha256", item.sha256())).toList();
        Files.writeString(target, objectMapper.writeValueAsString(Map.of(
                "formatVersion", 1, "batchId", batch.getId(), "batchNo", batch.getBatchNo(),
                "revisionNo", batch.getRevisionNo(), "generatedAt", Instant.now().toString(),
                "files", files)), StandardCharsets.UTF_8);
    }

    /**
     * 删除此前尝试上传但未落库的产物。调用点位于批次仍为 DRAFT 时，
     * 说明历史尝试都没有走到 persist（persist 会把批次置为 GENERATED），
     * 因此这些对象一定没有被 export_artifact 引用，可以安全删除。
     */
    private void discardAbandonedAttempts(AsyncJob job) {
        long current = job.getLeaseVersion() == null ? 0 : job.getLeaseVersion();
        for (long attempt = 1; attempt < current; attempt++) {
            String prefix = attemptPrefix(job, attempt);
            for (String fileName : ARTIFACT_FILE_NAMES) {
                try {
                    storage.delete(prefix + fileName);
                } catch (RuntimeException ignored) {
                    // 清理是尽力而为，删不掉不应阻断本次生成
                }
            }
        }
    }

    private String attemptPrefix(AsyncJob job, Long leaseVersion) {
        return job.getOrganizationId() + "/exports/" + job.getTargetId() + "/"
                + job.getId() + "/attempt-" + leaseVersion + "/";
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "导出产物生成失败" : message;
    }
}
