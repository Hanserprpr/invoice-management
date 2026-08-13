package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.ExportArtifact;
import cn.sduonline.invoice.data.po.FileObject;
import cn.sduonline.invoice.mapper.ExportArtifactMapper;
import cn.sduonline.invoice.mapper.ExportBatchMapper;
import cn.sduonline.invoice.mapper.FileObjectMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ExportArtifactPersistenceService {
    public record GeneratedArtifact(String fileId, String storageKey, String fileName,
                                    String relativePath, String artifactType, String contentType,
                                    long sizeBytes, String sha256) {
    }

    private final FileObjectMapper fileMapper;
    private final ExportArtifactMapper artifactMapper;
    private final ExportBatchMapper batchMapper;
    private final AuditService auditService;

    public ExportArtifactPersistenceService(FileObjectMapper fileMapper,
                                            ExportArtifactMapper artifactMapper,
                                            ExportBatchMapper batchMapper,
                                            AuditService auditService) {
        this.fileMapper = fileMapper;
        this.artifactMapper = artifactMapper;
        this.batchMapper = batchMapper;
        this.auditService = auditService;
    }

    @Transactional
    public void persist(String organizationId, String batchId, String jobId,
                        String actorCasId, List<GeneratedArtifact> generated) {
        Instant now = Instant.now();
        for (GeneratedArtifact item : generated) {
            fileMapper.insert(FileObject.builder().id(item.fileId()).organizationId(organizationId)
                    .uploaderCasId(actorCasId).storageKey(item.storageKey())
                    .originalName(item.fileName()).contentType(item.contentType())
                    .sizeBytes(item.sizeBytes()).sha256(item.sha256())
                    .purpose("EXPORT_ARTIFACT").scanStatus("READY").readyAt(now).build());
            artifactMapper.insert(ExportArtifact.builder().id(UlidGenerator.next())
                    .organizationId(organizationId).batchId(batchId).jobId(jobId)
                    .artifactType(item.artifactType()).fileId(item.fileId())
                    .relativePath(item.relativePath()).sha256(item.sha256()).build());
        }
        if (batchMapper.markGenerated(organizationId, batchId, now) != 1) {
            throw new IllegalStateException("EXPORT_BATCH_STATE_CHANGED");
        }
        auditService.append(organizationId, actorCasId, "EXPORT_BATCH_GENERATED", "EXPORT_BATCH",
                batchId, "{\"artifactCount\":" + generated.size() + "}");
    }
}
