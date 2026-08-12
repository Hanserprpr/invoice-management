package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.PaperDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.PaperEvent;
import cn.sduonline.invoice.data.po.PaperItem;
import cn.sduonline.invoice.data.po.ScanEvent;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.PaperItemVO;
import cn.sduonline.invoice.data.vo.PaperScanResultVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.mapper.PaperEventMapper;
import cn.sduonline.invoice.mapper.PaperItemMapper;
import cn.sduonline.invoice.mapper.PaperItemMapper.PaperContext;
import cn.sduonline.invoice.mapper.ProjectMapper;
import cn.sduonline.invoice.mapper.ScanEventMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class PaperService {
    private static final Set<String> STATUSES = Set.of("PENDING_DELIVERY", "MEMBER_DECLARED",
            "CLUB_RECEIVED", "RETURNED_TO_MEMBER", "TRANSFERRED_EXTERNAL", "ARCHIVED", "EXCEPTION");

    private final PaperItemMapper itemMapper;
    private final PaperEventMapper eventMapper;
    private final ScanEventMapper scanMapper;
    private final ProjectMapper projectMapper;
    private final LedgerMapper ledgerMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final InvoiceQrParser qrParser;
    private final ObjectMapper objectMapper;

    public PaperService(PaperItemMapper itemMapper, PaperEventMapper eventMapper,
                        ScanEventMapper scanMapper, ProjectMapper projectMapper,
                        LedgerMapper ledgerMapper, AuthorizationService authorizationService,
                        AuditService auditService, InvoiceQrParser qrParser, ObjectMapper objectMapper) {
        this.itemMapper = itemMapper;
        this.eventMapper = eventMapper;
        this.scanMapper = scanMapper;
        this.projectMapper = projectMapper;
        this.ledgerMapper = ledgerMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.qrParser = qrParser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureForApproved(String invoiceId, String actorCasId) {
        PaperContext context = requireContext(invoiceId);
        if (!context.paperRequired()) return;
        if (!"INTERNALLY_APPROVED".equals(context.invoiceStatus())) {
            throw new BusinessException(BizCode.INVOICE_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        if (itemMapper.createPending(context.organizationId(), invoiceId) == 1) {
            append(context, null, "PENDING_DELIVERY", "CREATED", null, actorCasId, null);
            auditService.append(context.organizationId(), actorCasId, "PAPER_ITEM_CREATED",
                    "INVOICE", invoiceId, "{\"status\":\"PENDING_DELIVERY\"}");
        }
    }

    public PaperItemVO detail(String invoiceId) {
        requireInvoiceAccess(invoiceId);
        PaperContext context = requireContext(invoiceId);
        requirePaper(context);
        return toVO(requireItem(context), context.projectId());
    }

    public PageResult<PaperItemVO> list(long page, long pageSize, String projectId, String status) {
        authorizationService.requireReviewProject(projectId);
        var project = projectMapper.selectById(projectId);
        if (project == null) notFoundProject();
        if (!Boolean.TRUE.equals(project.getPaperRequired())) {
            throw new BusinessException(BizCode.PAPER_NOT_ENABLED, HttpStatus.CONFLICT);
        }
        if (status != null && !STATUSES.contains(status)) invalid();
        IPage<PaperItem> result = itemMapper.findForProject(new Page<>(page, pageSize),
                TenantContext.requireOrganizationId(), projectId, status);
        return new PageResult<>(result.getRecords().stream().map(item -> toVO(item, projectId)).toList(),
                page, pageSize, result.getTotal());
    }

    @Transactional
    public PaperItemVO declare(String invoiceId, String actorCasId, PaperVersionRequest request) {
        PaperContext context = requireContext(invoiceId);
        requirePaper(context);
        if (!actorCasId.equals(context.applicantCasId())) noPermission();
        PaperItem item = requireItem(context);
        return transition(context, item, "PENDING_DELIVERY", "MEMBER_DECLARED",
                "MEMBER_DECLARED", null, actorCasId, null, request.version());
    }

    @Transactional
    public PaperItemVO revokeDeclaration(String invoiceId, String actorCasId,
                                         PaperVersionRequest request) {
        PaperContext context = requireContext(invoiceId);
        requirePaper(context);
        if (!actorCasId.equals(context.applicantCasId())) noPermission();
        PaperItem item = requireItem(context);
        return transition(context, item, "MEMBER_DECLARED", "PENDING_DELIVERY",
                "MEMBER_DECLARATION_REVOKED", null, actorCasId, null, request.version());
    }

    @Transactional
    public PaperItemVO receive(String invoiceId, String actorCasId, PaperVersionRequest request) {
        PaperContext context = requireContext(invoiceId);
        requirePaper(context);
        authorizationService.requireReviewProject(context.projectId());
        PaperItem item = requireItem(context);
        if (!Set.of("PENDING_DELIVERY", "MEMBER_DECLARED", "RETURNED_TO_MEMBER")
                .contains(item.getStatus())) state();
        return transition(context, item, item.getStatus(), "CLUB_RECEIVED", "CLUB_RECEIVED",
                null, actorCasId, null, request.version());
    }

    @Transactional
    public PaperItemVO changeState(String invoiceId, String actorCasId, PaperStateRequest request) {
        PaperContext context = requireContext(invoiceId);
        requirePaper(context);
        authorizationService.requireProjectManage(context.projectId());
        PaperItem item = requireItem(context);
        if (!allowed(item.getStatus(), request.status())) state();
        return transition(context, item, item.getStatus(), request.status(), "MANUAL_CORRECTION",
                request.reason().trim(), actorCasId, null, request.version());
    }

    @Transactional
    public PaperScanResultVO scan(String projectId, String actorCasId, PaperScanRequest request) {
        authorizationService.requireReviewProject(projectId);
        var project = projectMapper.selectOne(new LambdaQueryWrapper<cn.sduonline.invoice.data.po.Project>()
                .eq(cn.sduonline.invoice.data.po.Project::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(cn.sduonline.invoice.data.po.Project::getId, projectId));
        if (project == null) notFoundProject();
        if (!Boolean.TRUE.equals(project.getPaperRequired())) {
            throw new BusinessException(BizCode.PAPER_NOT_ENABLED, HttpStatus.CONFLICT);
        }
        String scanId = UlidGenerator.next();
        InvoiceQrParser.Identity identity;
        try {
            identity = qrParser.parse(request.rawPayload());
        } catch (BusinessException exception) {
            persistScan(scanId, projectId, actorCasId, request.rawPayload(), null,
                    null, "UNSUPPORTED", "无法解析二维码票据标识");
            return new PaperScanResultVO(scanId, "UNSUPPORTED", null, null);
        }
        List<String> matches = itemMapper.findInvoiceForScan(TenantContext.requireOrganizationId(),
                projectId, identity.digitalInvoiceNo(), identity.invoiceCode(), identity.invoiceNumber());
        if (matches.isEmpty()) {
            persistScan(scanId, projectId, actorCasId, request.rawPayload(), identity,
                    null, "NOT_FOUND", "当前项目未找到对应发票");
            return new PaperScanResultVO(scanId, "NOT_FOUND", null, null);
        }
        if (matches.size() > 1) {
            persistScan(scanId, projectId, actorCasId, request.rawPayload(), identity,
                    null, "POSSIBLE_DUPLICATE", "当前项目存在多个候选记录");
            return new PaperScanResultVO(scanId, "POSSIBLE_DUPLICATE", null, null);
        }
        String invoiceId = matches.getFirst();
        PaperContext context = requireContext(invoiceId);
        PaperItem item = requireItem(context);
        if ("CLUB_RECEIVED".equals(item.getStatus())) {
            persistScan(scanId, projectId, actorCasId, request.rawPayload(), identity,
                    invoiceId, "ALREADY_SCANNED", "纸票已由社团收取");
            return new PaperScanResultVO(scanId, "ALREADY_SCANNED", invoiceId,
                    toVO(item, projectId));
        }
        if (!Set.of("PENDING_DELIVERY", "MEMBER_DECLARED", "RETURNED_TO_MEMBER")
                .contains(item.getStatus())) {
            persistScan(scanId, projectId, actorCasId, request.rawPayload(), identity,
                    invoiceId, "DATA_MISMATCH", "纸票当前状态不允许收取");
            return new PaperScanResultVO(scanId, "DATA_MISMATCH", invoiceId, toVO(item, projectId));
        }
        persistScan(scanId, projectId, actorCasId, request.rawPayload(), identity,
                invoiceId, "SUCCESS", "已匹配并登记社团收取");
        PaperItemVO updated = transition(context, item, item.getStatus(), "CLUB_RECEIVED",
                "SCAN_RECEIVED", null, actorCasId, scanId, item.getVersion());
        return new PaperScanResultVO(scanId, "SUCCESS", invoiceId, updated);
    }

    private PaperItemVO transition(PaperContext context, PaperItem item, String from, String to,
                                   String eventType, String reason, String actorCasId,
                                   String scanId, long version) {
        if (item.getVersion() == null || item.getVersion() != version) versionConflict();
        Instant declared = "MEMBER_DECLARED".equals(to) ? Instant.now()
                : "PENDING_DELIVERY".equals(to) ? null : item.getMemberDeclaredAt();
        String receiver = "CLUB_RECEIVED".equals(to) ? actorCasId
                : "PENDING_DELIVERY".equals(to) || "RETURNED_TO_MEMBER".equals(to) ? null
                : item.getReceivedByCasId();
        Instant receivedAt = "CLUB_RECEIVED".equals(to) ? Instant.now()
                : receiver == null ? null : item.getReceivedAt();
        if (itemMapper.transition(context.organizationId(), context.invoiceId(), from, to,
                declared, receiver, receivedAt, reason, version) != 1) {
            PaperItem fresh = itemMapper.selectById(context.invoiceId());
            if (fresh != null && fresh.getVersion() != version) versionConflict();
            state();
        }
        append(context, from, to, eventType, reason, actorCasId, scanId);
        auditService.append(context.organizationId(), actorCasId, "PAPER_STATE_CHANGED", "INVOICE",
                context.invoiceId(), "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
        return toVO(itemMapper.selectById(context.invoiceId()), context.projectId());
    }

    private void persistScan(String id, String projectId, String actorCasId, String raw,
                             InvoiceQrParser.Identity identity, String invoiceId,
                             String result, String detail) {
        scanMapper.insert(ScanEvent.builder().id(id).organizationId(TenantContext.requireOrganizationId())
                .invoiceId(invoiceId).projectId(projectId).actorCasId(actorCasId)
                .context("PAPER_RECEIPT").rawHash(sha256(raw))
                .parsedIdentityJson(identity == null ? null : writeJson(identity))
                .result(result).resultDetail(detail).build());
    }

    private void append(PaperContext context, String from, String to, String eventType,
                        String reason, String actorCasId, String scanId) {
        eventMapper.insert(PaperEvent.builder().id(UlidGenerator.next())
                .organizationId(context.organizationId()).invoiceId(context.invoiceId())
                .fromStatus(from).toStatus(to).eventType(eventType).reason(reason)
                .actorCasId(actorCasId).scanEventId(scanId).build());
    }

    private PaperItemVO toVO(PaperItem item, String projectId) {
        List<PaperItemVO.EventVO> events = eventMapper
                .findForInvoice(item.getOrganizationId(), item.getInvoiceId()).stream()
                .map(e -> new PaperItemVO.EventVO(e.getId(), e.getFromStatus(), e.getToStatus(),
                        e.getEventType(), e.getReason(), e.getActorCasId(), e.getScanEventId(),
                        e.getCreatedAt())).toList();
        return new PaperItemVO(item.getInvoiceId(), projectId, item.getStatus(),
                item.getMemberDeclaredAt(), item.getReceivedByCasId(), item.getReceivedAt(),
                item.getNote(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt(), events);
    }

    private PaperContext requireContext(String invoiceId) {
        PaperContext context = itemMapper.findContext(TenantContext.requireOrganizationId(), invoiceId);
        if (context == null) throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        return context;
    }

    private PaperItem requireItem(PaperContext context) {
        PaperItem item = itemMapper.selectOne(new LambdaQueryWrapper<PaperItem>()
                .eq(PaperItem::getOrganizationId, context.organizationId())
                .eq(PaperItem::getInvoiceId, context.invoiceId()));
        if (item == null) throw new BusinessException(BizCode.PAPER_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        return item;
    }

    private void requirePaper(PaperContext context) {
        if (!context.paperRequired()) {
            throw new BusinessException(BizCode.PAPER_NOT_ENABLED, HttpStatus.CONFLICT);
        }
    }

    private void requireInvoiceAccess(String invoiceId) {
        boolean unrestricted = authorizationService.hasPermission("application:review")
                || authorizationService.hasPermission("audit-log:read");
        if (!ledgerMapper.canAccessInvoice(TenantContext.requireOrganizationId(),
                TenantContext.requireCasId(), unrestricted, invoiceId)) noPermission();
    }

    private boolean allowed(String from, String to) {
        return switch (to) {
            case "RETURNED_TO_MEMBER" -> Set.of("CLUB_RECEIVED", "EXCEPTION").contains(from);
            case "TRANSFERRED_EXTERNAL" -> "CLUB_RECEIVED".equals(from);
            case "ARCHIVED" -> Set.of("CLUB_RECEIVED", "TRANSFERRED_EXTERNAL").contains(from);
            case "EXCEPTION" -> !Set.of("ARCHIVED", "TRANSFERRED_EXTERNAL").contains(from);
            case "PENDING_DELIVERY" -> Set.of("RETURNED_TO_MEMBER", "EXCEPTION").contains(from);
            default -> false;
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void invalid() { throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST); }
    private void state() { throw new BusinessException(BizCode.PAPER_STATE_NOT_ALLOWED, HttpStatus.CONFLICT); }
    private void versionConflict() { throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT); }
    private void noPermission() { throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN); }
    private void notFoundProject() { throw new BusinessException(BizCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND); }
}
