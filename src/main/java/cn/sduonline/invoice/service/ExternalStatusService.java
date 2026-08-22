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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExternalStatusService {
    private final ExternalStatusEventMapper eventMapper;
    private final ExternalStatusEventAttachmentMapper attachmentMapper;
    private final ExportBatchMapper batchMapper;
    private final ExportLifecycleService lifecycle;
    private final AuthorizationService authorizationService;
    private final FileObjectService fileService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ExternalStatusService(ExternalStatusEventMapper eventMapper,
                                 ExternalStatusEventAttachmentMapper attachmentMapper,
                                 ExportBatchMapper batchMapper,
                                 ExportLifecycleService lifecycle,
                                 AuthorizationService authorizationService,
                                 FileObjectService fileService,
                                 NotificationService notificationService,
                                 AuditService auditService) {
        this.eventMapper = eventMapper;
        this.attachmentMapper = attachmentMapper;
        this.batchMapper = batchMapper;
        this.lifecycle = lifecycle;
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
        if ("COMMENT".equals(request.eventType())
                && !Set.of("GENERATED", "EXPORTED", "EXTERNAL_PROCESSING", "COMPLETED")
                .contains(batch.getStatus())) state();
        return persist(batch, actorCasId, request.eventType(), request.status(), null,
                clean(request.comment()), request.attachmentFileIds(), true);
    }

    @Transactional
    public ExternalStatusEventVO correct(String batchId, String eventId, String actorCasId,
                                         CorrectExternalEventRequest request) {
        ExportBatch batch = prepare(batchId, request.batchVersion());
        ExternalStatusEvent corrected = eventMapper.findOne(batch.getOrganizationId(), batchId, eventId);
        if (corrected == null) {
            throw new BusinessException(BizCode.EXTERNAL_STATUS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!"STATUS_CHANGE".equals(corrected.getEventType())) {
            throw new BusinessException(BizCode.EXTERNAL_STATUS_CORRECTION_INVALID, HttpStatus.CONFLICT);
        }
        return persist(batch, actorCasId, "CORRECTION", request.status(), eventId,
                request.reason().trim(), request.attachmentFileIds(),
                !"ARCHIVED".equals(batch.getStatus()));
    }

    private ExportBatch prepare(String batchId, long version) {
        ExportBatch batch = requireBatch(batchId);
        authorizationService.requireProjectManage(batch.getProjectId());
        if (batch.getVersion() == null || batch.getVersion() != version) conflict();
        // 平台外状态记录的是“材料离开平台后”的处理结果，草稿批次尚未生成任何产物，
        // 不应存在平台外事件；取消草稿批次请走导出批次自身的取消接口。
        if ("DRAFT".equals(batch.getStatus())) state();
        return batch;
    }

    private ExternalStatusEventVO persist(ExportBatch batch, String actorCasId, String eventType,
                                          String status, String correctionOf, String comment,
                                          List<String> fileIds, boolean applyState) {
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
        if (applyState && !"COMMENT".equals(eventType)) applyBatchState(batch, status);
        if (applyState && !"COMMENT".equals(eventType) && "RETURNED_EXTERNAL".equals(status)) {
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
        ExportLifecycleService.Action action = switch (externalStatus) {
            case "PENDING_EXTERNAL" -> ExportLifecycleService.Action.PENDING_EXTERNAL;
            case "SUBMITTED_EXTERNAL", "RETURNED_EXTERNAL" ->
                    ExportLifecycleService.Action.EXTERNAL_PROCESSING;
            case "COMPLETED" -> ExportLifecycleService.Action.COMPLETE;
            case "CANCELLED" -> ExportLifecycleService.Action.CANCEL;
            case "ARCHIVED" -> ExportLifecycleService.Action.FINAL_ARCHIVE;
            default -> throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        };
        lifecycle.transition(batch, batch.getVersion(), action);
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
