package cn.sduonline.invoice.data.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExportBatchVO(
        String id, String projectId, String batchNo, int revisionNo, String status,
        int invoiceCount, BigDecimal totalFaceAmount, BigDecimal totalClaimedAmount,
        long version, String createdByCasId, Instant createdAt, Instant generatedAt,
        Instant firstDownloadedAt, Instant completedAt, Instant archivedAt, Instant cancelledAt,
        List<InvoiceSnapshotVO> invoices, List<ArtifactVO> artifacts, JobVO generationJob) {

    public record InvoiceSnapshotVO(String invoiceId, int sequenceNo, BigDecimal faceAmount,
                                    BigDecimal claimedAmount) {
    }

    public record ArtifactVO(String id, String artifactType, String relativePath,
                             String sha256, Instant createdAt) {
    }

    public record JobVO(String id, String status, int progress, int attempts,
                        String errorCode, String errorMessage, Instant createdAt, Instant finishedAt) {
    }
}
