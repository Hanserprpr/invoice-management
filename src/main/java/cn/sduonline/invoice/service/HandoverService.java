package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.HandoverDtos.CreateHandoverRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.HandoverRecord;
import cn.sduonline.invoice.data.po.OrganizationMember;
import cn.sduonline.invoice.data.vo.HandoverRecordVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.HandoverRecordMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class HandoverService {
    private final HandoverRecordMapper mapper;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public HandoverService(HandoverRecordMapper mapper, AuthorizationService authorizationService,
                           NotificationService notificationService, AuditService auditService,
                           ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<HandoverRecordVO> list() {
        authorizationService.requirePermission("member:manage");
        return mapper.findForOrganization(TenantContext.requireOrganizationId()).stream()
                .map(this::toVO).toList();
    }

    @Transactional
    public HandoverRecordVO create(String actorCasId, CreateHandoverRequest request) {
        authorizationService.requirePermission("member:manage");
        if (request.outgoingCasId().equals(request.incomingCasId())) invalid();
        String organizationId = TenantContext.requireOrganizationId();
        OrganizationMember outgoing = mapper.lockMember(organizationId, request.outgoingCasId());
        OrganizationMember incoming = mapper.lockMember(organizationId, request.incomingCasId());
        if (outgoing == null || incoming == null) {
            throw new BusinessException(BizCode.MEMBERSHIP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!"ACTIVE".equals(outgoing.getStatus()) || !"ACTIVE".equals(incoming.getStatus())) state();
        if (outgoing.getVersion() == null || outgoing.getVersion() != request.outgoingVersion()) conflict();
        List<String> projects = mapper.findManagedProjects(organizationId, outgoing.getId());
        int accessCount = mapper.countAccess(organizationId, outgoing.getId());
        mapper.copyManagers(organizationId, outgoing.getId(), incoming.getId(), actorCasId);
        mapper.copyAccess(organizationId, outgoing.getId(), incoming.getId(), actorCasId);
        mapper.deleteManagers(organizationId, outgoing.getId());
        mapper.deleteAccess(organizationId, outgoing.getId());
        if (mapper.endMembership(organizationId, outgoing.getId(), request.outgoingVersion(),
                LocalDate.now(), Instant.now()) != 1) conflict();
        String id = UlidGenerator.next();
        HandoverRecord record = HandoverRecord.builder().id(id).organizationId(organizationId)
                .outgoingMemberId(outgoing.getId()).incomingMemberId(incoming.getId())
                .performedByCasId(actorCasId).snapshotJson(write(Map.of(
                        "outgoingCasId", outgoing.getCasId(), "incomingCasId", incoming.getCasId(),
                        "projectIds", projects, "projectAccessCount", accessCount)))
                .comment(clean(request.comment())).build();
        mapper.insert(record);
        notificationService.createDeduplicated(organizationId, incoming.getCasId(),
                "HANDOVER_RECEIVED", "社团工作已交接给你",
                "你已接收 " + projects.size() + " 个项目的负责人或访问权限。",
                "HANDOVER", id, "handover:" + id + ":incoming");
        auditService.append(organizationId, actorCasId, "HANDOVER_COMPLETED", "HANDOVER", id,
                "{\"outgoingCasId\":\"" + outgoing.getCasId()
                        + "\",\"incomingCasId\":\"" + incoming.getCasId()
                        + "\",\"projectCount\":" + projects.size() + "}");
        return new HandoverRecordVO(id, outgoing.getCasId(), incoming.getCasId(), actorCasId,
                projects, accessCount, record.getComment(), record.getCreatedAt());
    }

    private HandoverRecordVO toVO(HandoverRecord row) {
        try {
            Map<String, Object> snapshot = objectMapper.readValue(row.getSnapshotJson(),
                    new TypeReference<Map<String, Object>>() { });
            @SuppressWarnings("unchecked")
            List<String> projects = (List<String>) snapshot.getOrDefault("projectIds", List.of());
            int accessCount = ((Number) snapshot.getOrDefault("projectAccessCount", 0)).intValue();
            return new HandoverRecordVO(row.getId(), String.valueOf(snapshot.get("outgoingCasId")),
                    String.valueOf(snapshot.get("incomingCasId")), row.getPerformedByCasId(),
                    projects, accessCount, row.getComment(), row.getCreatedAt());
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void invalid() { throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST); }
    private void state() { throw new BusinessException(BizCode.HANDOVER_STATE_INVALID, HttpStatus.CONFLICT); }
    private void conflict() { throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT); }
}
