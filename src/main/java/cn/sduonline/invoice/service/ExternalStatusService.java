package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.ExternalStatusDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.*;
import cn.sduonline.invoice.data.vo.ExternalStatusEventVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.*;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExternalStatusService {
    private final ExternalStatusEventMapper eventMapper;
    private final ExternalStatusEventAttachmentMapper attachmentMapper;
    private final ExportBatchMapper batchMapper;
    private final InvoiceExportReservationMapper reservationMapper;
    private final AuthorizationService authorizationService;
    private final FileObjectService fileService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ExternalStatusService(ExternalStatusEventMapper eventMapper,
                                 ExternalStatusEventAttachmentMapper attachmentMapper,
                                 ExportBatchMapper batchMapper,
                                 InvoiceExportReservationMapper reservationMapper,
                                 AuthorizationService authorizationService,
                                 FileObjectService fileService,
                                 NotificationService notificationService,
                                 AuditService auditService) {
        this.eventMapper = eventMapper;
        this.attachmentMapper = attachmentMapper;
        this.batchMapper = batchMapper;
        this.reservationMapper = reservationMapper;
        this.authorizationService = authorizationService;
        this.fileService = fileService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public List<ExternalStatusEventVO> list(String batchId) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireReviewProject(batch.getProjectId());
        return eventMapper.findForBatch(batch.getOrganizationId(), batchId).stream()
                .map(this::toVO).toList();
    }

    @Transactional
    public ExternalStatusEventVO append(String batchId, String actorCasId,
                                        CreateExternalEventRequest request) {
        ExportBatch batch = prepare(batchId, request.batchVersion());
        if ("COMMENT".equals(request.eventType())
                && (request.comment() == null || request.comment().isBlank())) invalid();
        return persist(batch, actorCasId, request.eventType(), request.status(), null,
                clean(request.comment()), request.attachmentFileIds());
    }

    @Transactional
    public ExternalStatusEventVO correct(String batchId, String eventId, String actorCasId,
                                         CorrectExternalEventRequest request) {
        ExportBatch batch = prepare(batchId, request.batchVersion());
        ExternalStatusEvent corrected = eventMapper.findOne(batch.getOrganizationId(), batchId, eventId);
        if (corrected == null) {
            throw new BusinessException(BizCode.EXTERNAL_STATUS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if ("CORRECTION".equals(corrected.getEventType())) {
            throw new BusinessException(BizCode.EXTERNAL_STATUS_CORRECTION_INVALID, HttpStatus.CONFLICT);
        }
        return persist(batch, actorCasId, "CORRECTION", request.status(), eventId,
                request.reason().trim(), request.attachmentFileIds());
    }

    private ExportBatch prepare(String batchId, long version) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireProjectManage(batch.getProjectId());
        if (batch.getVersion() == null || batch.getVersion() != version) conflict();
        if (!Set.of("GENERATED", "EXPORTED", "EXTERNAL_PROCESSING", "COMPLETED")
                .contains(batch.getStatus())) state();
        return batch;
    }

    private ExternalStatusEventVO persist(ExportBatch batch, String actorCasId, String eventType,
                                          String status, String correctionOf, String comment,
                                          List<String> fileIds) {
        Set<String> attachments = fileIds == null ? Set.of() : new LinkedHashSet<>(fileIds);
        if (fileIds != null && attachments.size() != fileIds.size()) invalid();
        for (String fileId : attachments) {
            fileService.requireReadyOwned(fileId, actorCasId, Set.of("OTHER", "FORM_ATTACHMENT"));
        }
        String id = UlidGenerator.next();
        ExternalStatusEvent event = ExternalStatusEvent.builder().id(id)
                .organizationId(batch.getOrganizationId()).batchId(batch.getId())
                .actorCasId(actorCasId).eventType(eventType).status(status)
                .correctionOfEventId(correctionOf).comment(comment).build();
        eventMapper.insert(event);
        for (String fileId : attachments) {
            attachmentMapper.insert(ExternalStatusEventAttachment.builder().eventId(id)
                    .organizationId(batch.getOrganizationId()).fileId(fileId).build());
            fileService.markReferenced(fileId);
        }
        applyBatchState(batch, status);
        if ("RETURNED_EXTERNAL".equals(status)) {
            notificationService.createDeduplicated(batch.getOrganizationId(),
                    batch.getCreatedByCasId(), "EXTERNAL_BATCH_RETURNED", "导出批次被退回",
                    "批次 " + batch.getBatchNo() + " 已在平台外退回，请及时处理。",
                    "EXPORT_BATCH", batch.getId(), "external-return:" + id);
        }
        auditService.append(batch.getOrganizationId(), actorCasId, "EXTERNAL_STATUS_APPENDED",
                "EXTERNAL_STATUS_EVENT", id, "{\"batchId\":\"" + batch.getId()
                        + "\",\"status\":\"" + status + "\",\"eventType\":\""
                        + eventType + "\"}");
        return toVO(event);
    }

    private void applyBatchState(ExportBatch batch, String externalStatus) {
        String next = switch (externalStatus) {
            case "SUBMITTED_EXTERNAL", "RETURNED_EXTERNAL" -> "EXTERNAL_PROCESSING";
            case "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            case "ARCHIVED" -> "ARCHIVED";
            default -> batch.getStatus();
        };
        batch.setStatus(next);
        if ("COMPLETED".equals(next)) batch.setCompletedAt(Instant.now());
        if ("CANCELLED".equals(next)) batch.setCancelledAt(Instant.now());
        if ("ARCHIVED".equals(next)) batch.setArchivedAt(Instant.now());
        long version = batch.getVersion();
        batch.setVersion(version);
        if (batchMapper.updateById(batch) != 1) conflict();
        batch.setVersion(version + 1);
        if ("CANCELLED".equals(next)) {
            reservationMapper.deleteForBatch(batch.getOrganizationId(), batch.getId());
        }
    }

    private ExportBatch requireBatch(String id) {
        ExportBatch batch = batchMapper.findOne(TenantContext.requireOrganizationId(), id);
        if (batch == null) {
            throw new BusinessException(BizCode.EXPORT_BATCH_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return batch;
    }

    private ExternalStatusEventVO toVO(ExternalStatusEvent event) {
        return new ExternalStatusEventVO(event.getId(), event.getBatchId(), event.getActorCasId(),
                event.getEventType(), event.getStatus(), event.getCorrectionOfEventId(),
                event.getComment(), attachmentMapper.findFileIds(event.getOrganizationId(),
                event.getId()), event.getCreatedAt());
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void invalid() { throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST); }
    private void state() { throw new BusinessException(BizCode.EXPORT_BATCH_STATE_NOT_ALLOWED, HttpStatus.CONFLICT); }
    private void conflict() { throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT); }
}
