package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.ExportDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.*;
import cn.sduonline.invoice.data.vo.ExportBatchVO;
import cn.sduonline.invoice.data.vo.FileDownloadVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.*;
import cn.sduonline.invoice.mapper.ExportBatchInvoiceMapper.Candidate;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExportBatchService {
    private static final String JOB_TYPE = "EXPORT_GENERATION";
    private static final Set<String> STATUSES = Set.of("DRAFT", "GENERATED", "EXPORTED",
            "EXTERNAL_PROCESSING", "COMPLETED", "CANCELLED", "ARCHIVED");

    private final ExportBatchMapper batchMapper;
    private final ExportBatchInvoiceMapper itemMapper;
    private final InvoiceExportReservationMapper reservationMapper;
    private final ExportArtifactMapper artifactMapper;
    private final AsyncJobMapper jobMapper;
    private final FileObjectMapper fileMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    public ExportBatchService(ExportBatchMapper batchMapper, ExportBatchInvoiceMapper itemMapper,
                              InvoiceExportReservationMapper reservationMapper,
                              ExportArtifactMapper artifactMapper, AsyncJobMapper jobMapper,
                              FileObjectMapper fileMapper, AuthorizationService authorizationService,
                              AuditService auditService, ObjectStorage objectStorage,
                              ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.reservationMapper = reservationMapper;
        this.artifactMapper = artifactMapper;
        this.jobMapper = jobMapper;
        this.fileMapper = fileMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
    }

    public PageResult<ExportBatchVO> list(long page, long pageSize, String projectId, String status) {
        if (status != null && !STATUSES.contains(status)) invalid();
        if (projectId != null) authorizationService.requireReviewProject(projectId);
        boolean unrestricted = authorizationService.hasPermission("application:review");
        var result = batchMapper.findPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                TenantContext.requireOrganizationId(), TenantContext.requireCasId(), unrestricted,
                projectId, status);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                page, pageSize, result.getTotal());
    }

    public ExportBatchVO detail(String batchId) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireReviewProject(batch.getProjectId());
        return toVO(batch);
    }

    @Transactional
    public ExportBatchVO create(String actorCasId, CreateExportBatchRequest request) {
        String organizationId = TenantContext.requireOrganizationId();
        List<String> invoiceIds = new ArrayList<>(request.invoiceIds());
        if (new HashSet<>(invoiceIds).size() != invoiceIds.size()) invalid();
        List<Candidate> candidates = itemMapper.lockCandidates(organizationId, invoiceIds);
        if (candidates.size() != invoiceIds.size()) {
            throw new BusinessException(BizCode.EXPORT_INVOICE_NOT_AVAILABLE, HttpStatus.CONFLICT);
        }
        String projectId = candidates.getFirst().projectId();
        if (candidates.stream().anyMatch(row -> !projectId.equals(row.projectId()))) {
            throw new BusinessException(BizCode.EXPORT_INVOICE_CROSS_PROJECT, HttpStatus.BAD_REQUEST);
        }
        authorizationService.requireReviewProject(projectId);
        String id = UlidGenerator.next();
        BigDecimal faceTotal = candidates.stream().map(Candidate::faceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal claimedTotal = candidates.stream().map(Candidate::claimedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ExportBatch batch = ExportBatch.builder().id(id).organizationId(organizationId)
                .projectId(projectId).batchNo(request.batchNo()).revisionNo(1).status("DRAFT")
                .invoiceCount(candidates.size()).totalFaceAmount(faceTotal)
                .totalClaimedAmount(claimedTotal).snapshotJson(write(Map.of(
                        "projectId", projectId, "invoiceIds", invoiceIds)))
                .version(0L).createdByCasId(actorCasId).build();
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(BizCode.DUPLICATE_SUBMIT, HttpStatus.CONFLICT);
        }
        try {
            Map<String, Candidate> byId = candidates.stream().collect(
                    java.util.stream.Collectors.toMap(Candidate::invoiceId, row -> row));
            for (int index = 0; index < invoiceIds.size(); index++) {
                Candidate row = byId.get(invoiceIds.get(index));
                itemMapper.insert(ExportBatchInvoice.builder().batchId(id)
                        .organizationId(organizationId).invoiceId(row.invoiceId())
                        .sequenceNo(index + 1).snapshotFaceAmount(row.faceAmount())
                        .snapshotClaimedAmount(row.claimedAmount()).snapshotJson(write(row)).build());
                reservationMapper.insert(InvoiceExportReservation.builder()
                        .invoiceId(row.invoiceId()).organizationId(organizationId).batchId(id).build());
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(BizCode.EXPORT_INVOICE_RESERVED, HttpStatus.CONFLICT);
        }
        auditService.append(organizationId, actorCasId, "EXPORT_BATCH_CREATED", "EXPORT_BATCH", id,
                "{\"projectId\":\"" + projectId + "\",\"invoiceCount\":" + candidates.size() + "}");
        return toVO(batch);
    }

    @Transactional
    public ExportBatchVO generate(String batchId, String actorCasId, BatchVersionRequest request) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireReviewProject(batch.getProjectId());
        requireVersion(batch, request.version());
        if (!"DRAFT".equals(batch.getStatus())) state();
        AsyncJob active = jobMapper.findActiveForTarget(batch.getOrganizationId(), JOB_TYPE,
                "EXPORT_BATCH", batchId);
        if (active == null) {
            jobMapper.insert(AsyncJob.builder().id(UlidGenerator.next())
                    .organizationId(batch.getOrganizationId()).jobType(JOB_TYPE)
                    .targetType("EXPORT_BATCH").targetId(batchId).status("PENDING")
                    .progress(0).attemptCount(0).maxAttempts(3).createdByCasId(actorCasId).build());
            auditService.append(batch.getOrganizationId(), actorCasId, "EXPORT_GENERATION_REQUESTED",
                    "EXPORT_BATCH", batchId, "{\"revisionNo\":" + batch.getRevisionNo() + "}");
        }
        return toVO(batch);
    }

    @Transactional
    public ExportBatchVO revise(String batchId, String actorCasId, BatchVersionRequest request) {
        String organizationId = TenantContext.requireOrganizationId();
        ExportBatch previous = batchMapper.lockOne(organizationId, batchId);
        if (previous == null) {
            throw new BusinessException(BizCode.EXPORT_BATCH_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        authorizationService.requireReviewProject(previous.getProjectId());
        requireVersion(previous, request.version());
        if (!Set.of("GENERATED", "EXPORTED").contains(previous.getStatus())) state();
        List<ExportBatchInvoice> snapshots = itemMapper.findForBatch(organizationId, batchId);
        int revision = batchMapper.maxRevision(organizationId, previous.getProjectId(),
                previous.getBatchNo()) + 1;
        String newId = UlidGenerator.next();
        ExportBatch next = ExportBatch.builder().id(newId).organizationId(organizationId)
                .projectId(previous.getProjectId()).batchNo(previous.getBatchNo())
                .revisionNo(revision).status("DRAFT").invoiceCount(previous.getInvoiceCount())
                .totalFaceAmount(previous.getTotalFaceAmount())
                .totalClaimedAmount(previous.getTotalClaimedAmount())
                .snapshotJson(previous.getSnapshotJson()).version(0L).createdByCasId(actorCasId).build();
        batchMapper.insert(next);
        for (ExportBatchInvoice snapshot : snapshots) {
            itemMapper.insert(ExportBatchInvoice.builder().batchId(newId).organizationId(organizationId)
                    .invoiceId(snapshot.getInvoiceId()).sequenceNo(snapshot.getSequenceNo())
                    .snapshotFaceAmount(snapshot.getSnapshotFaceAmount())
                    .snapshotClaimedAmount(snapshot.getSnapshotClaimedAmount())
                    .snapshotJson(snapshot.getSnapshotJson()).build());
        }
        reservationMapper.transferBatch(organizationId, batchId, newId);
        previous.setStatus("ARCHIVED");
        previous.setArchivedAt(Instant.now());
        previous.setVersion(request.version());
        if (batchMapper.updateById(previous) != 1) conflict();
        auditService.append(organizationId, actorCasId, "EXPORT_BATCH_REVISION_CREATED",
                "EXPORT_BATCH", newId, "{\"previousBatchId\":\"" + batchId
                        + "\",\"revisionNo\":" + revision + "}");
        return toVO(next);
    }

    @Transactional
    public ExportBatchVO cancel(String batchId, String actorCasId, BatchVersionRequest request) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireReviewProject(batch.getProjectId());
        requireVersion(batch, request.version());
        if (!Set.of("DRAFT", "GENERATED", "EXPORTED").contains(batch.getStatus())) state();
        batch.setStatus("CANCELLED");
        batch.setCancelledAt(Instant.now());
        batch.setVersion(request.version());
        if (batchMapper.updateById(batch) != 1) conflict();
        reservationMapper.deleteForBatch(batch.getOrganizationId(), batchId);
        batch.setVersion(request.version() + 1);
        auditService.append(batch.getOrganizationId(), actorCasId, "EXPORT_BATCH_CANCELLED",
                "EXPORT_BATCH", batchId, "{}");
        return toVO(batch);
    }

    @Transactional
    public ExportBatchVO complete(String batchId, String actorCasId, BatchVersionRequest request) {
        return terminal(batchId, actorCasId, request, "COMPLETED");
    }

    @Transactional
    public ExportBatchVO archive(String batchId, String actorCasId, BatchVersionRequest request) {
        return terminal(batchId, actorCasId, request, "ARCHIVED");
    }

    @Transactional
    public FileDownloadVO download(String batchId, String artifactId, String actorCasId) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireReviewProject(batch.getProjectId());
        ExportArtifact artifact = artifactMapper.selectById(artifactId);
        if (artifact == null || !batchId.equals(artifact.getBatchId())
                || !batch.getOrganizationId().equals(artifact.getOrganizationId())) {
            throw new BusinessException(BizCode.EXPORT_ARTIFACT_NOT_READY, HttpStatus.NOT_FOUND);
        }
        FileObject file = fileMapper.findByOrganizationAndId(batch.getOrganizationId(), artifact.getFileId());
        if (file == null || !"READY".equals(file.getScanStatus())) {
            throw new BusinessException(BizCode.EXPORT_ARTIFACT_NOT_READY, HttpStatus.CONFLICT);
        }
        batchMapper.markDownloaded(batch.getOrganizationId(), batchId, Instant.now());
        ObjectStorage.DownloadGrant grant = objectStorage.createDownloadGrant(file.getStorageKey(),
                file.getOriginalName(), file.getContentType());
        auditService.append(batch.getOrganizationId(), actorCasId, "EXPORT_ARTIFACT_DOWNLOADED",
                "EXPORT_ARTIFACT", artifactId, "{\"batchId\":\"" + batchId + "\"}");
        return new FileDownloadVO(grant.url(), grant.expiresAt());
    }

    private ExportBatchVO terminal(String batchId, String actorCasId, BatchVersionRequest request,
                                   String target) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireProjectManage(batch.getProjectId());
        requireVersion(batch, request.version());
        if ("COMPLETED".equals(target) && !Set.of("EXPORTED", "EXTERNAL_PROCESSING").contains(batch.getStatus())
                || "ARCHIVED".equals(target) && !"COMPLETED".equals(batch.getStatus())) state();
        batch.setStatus(target);
        if ("COMPLETED".equals(target)) batch.setCompletedAt(Instant.now());
        else batch.setArchivedAt(Instant.now());
        batch.setVersion(request.version());
        if (batchMapper.updateById(batch) != 1) conflict();
        batch.setVersion(request.version() + 1);
        auditService.append(batch.getOrganizationId(), actorCasId, "EXPORT_BATCH_" + target,
                "EXPORT_BATCH", batchId, "{}");
        return toVO(batch);
    }

    private ExportBatch requireBatch(String id) {
        ExportBatch batch = batchMapper.findOne(TenantContext.requireOrganizationId(), id);
        if (batch == null) throw new BusinessException(BizCode.EXPORT_BATCH_NOT_FOUND, HttpStatus.NOT_FOUND);
        return batch;
    }

    private ExportBatchVO toVO(ExportBatch batch) {
        List<ExportBatchVO.InvoiceSnapshotVO> invoices = itemMapper
                .findForBatch(batch.getOrganizationId(), batch.getId()).stream()
                .map(item -> new ExportBatchVO.InvoiceSnapshotVO(item.getInvoiceId(),
                        item.getSequenceNo(), item.getSnapshotFaceAmount(),
                        item.getSnapshotClaimedAmount())).toList();
        List<ExportBatchVO.ArtifactVO> artifacts = artifactMapper
                .findForBatch(batch.getOrganizationId(), batch.getId()).stream()
                .map(item -> new ExportBatchVO.ArtifactVO(item.getId(), item.getArtifactType(),
                        item.getRelativePath(), item.getSha256(), item.getCreatedAt())).toList();
        AsyncJob job = jobMapper.findLatestForTarget(batch.getOrganizationId(), JOB_TYPE,
                "EXPORT_BATCH", batch.getId());
        ExportBatchVO.JobVO jobVO = job == null ? null : new ExportBatchVO.JobVO(job.getId(),
                job.getStatus(), job.getProgress(), job.getAttemptCount(), job.getErrorCode(),
                job.getErrorMessage(), job.getCreatedAt(), job.getFinishedAt());
        return new ExportBatchVO(batch.getId(), batch.getProjectId(), batch.getBatchNo(),
                batch.getRevisionNo(), batch.getStatus(), batch.getInvoiceCount(),
                batch.getTotalFaceAmount(), batch.getTotalClaimedAmount(), batch.getVersion(),
                batch.getCreatedByCasId(), batch.getCreatedAt(), batch.getGeneratedAt(),
                batch.getFirstDownloadedAt(), batch.getCompletedAt(), batch.getArchivedAt(),
                batch.getCancelledAt(), invoices, artifacts, jobVO);
    }

    private void requireVersion(ExportBatch batch, long version) {
        if (batch.getVersion() == null || batch.getVersion() != version) conflict();
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void invalid() { throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST); }
    private void state() { throw new BusinessException(BizCode.EXPORT_BATCH_STATE_NOT_ALLOWED, HttpStatus.CONFLICT); }
    private void conflict() { throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT); }
}
