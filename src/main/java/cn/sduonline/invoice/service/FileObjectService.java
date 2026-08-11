package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.CompleteUploadRequest;
import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.FileObject;
import cn.sduonline.invoice.data.vo.FileObjectVO;
import cn.sduonline.invoice.data.vo.FileUploadVO;
import cn.sduonline.invoice.data.vo.FileDownloadVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.FileObjectMapper;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
public class FileObjectService {
    private static final Set<String> TYPES = Set.of(
            "application/pdf", "application/ofd", "image/jpeg", "image/png");
    private static final DateTimeFormatter PATH_DATE =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final FileObjectMapper fileMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final LedgerMapper ledgerMapper;
    private final ObjectStorage objectStorage;
    private final FileScanJobService fileScanJobService;

    public FileObjectService(FileObjectMapper fileMapper,
                             AuthorizationService authorizationService,
                             AuditService auditService,
                             LedgerMapper ledgerMapper,
                             ObjectStorage objectStorage,
                             FileScanJobService fileScanJobService) {
        this.fileMapper = fileMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.ledgerMapper = ledgerMapper;
        this.objectStorage = objectStorage;
        this.fileScanJobService = fileScanJobService;
    }

    @Transactional
    public FileUploadVO createUpload(String actorCasId, RegisterFileRequest request) {
        FileObjectVO registered = register(actorCasId, request);
        String organizationId = TenantContext.requireOrganizationId();
        FileObject file = fileMapper.findByOrganizationAndId(organizationId, registered.id());
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if (!"PENDING".equals(file.getScanStatus())) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        ObjectStorage.UploadGrant grant = objectStorage.createUploadGrant(file.getStorageKey(),
                file.getContentType(), file.getSizeBytes(), file.getSha256());
        return new FileUploadVO(toVO(file), "PUT", grant.url(), grant.requiredHeaders(),
                grant.expiresAt());
    }

    @Transactional
    public FileObjectVO completeUpload(String actorCasId, String fileId,
                                       CompleteUploadRequest request) {
        String organizationId = TenantContext.requireOrganizationId();
        FileObject file = fileMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getOrganizationId, organizationId)
                .eq(FileObject::getId, fileId)
                .eq(FileObject::getUploaderCasId, actorCasId));
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if (!file.getSha256().equalsIgnoreCase(request.sha256())) throw uploadInvalid();
        if (Set.of("SCANNING", "READY").contains(file.getScanStatus())) return toVO(file);
        if (!"PENDING".equals(file.getScanStatus())) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        ObjectStorage.StoredObject stored = objectStorage.headUpload(file.getStorageKey())
                .orElseThrow(this::uploadInvalid);
        String storedHash = stored.metadata().get("sha256");
        if (stored.sizeBytes() != file.getSizeBytes()
                || stored.contentType() == null
                || !stored.contentType().equalsIgnoreCase(file.getContentType())
                || storedHash == null
                || !storedHash.equalsIgnoreCase(file.getSha256())) {
            throw uploadInvalid();
        }
        if (fileMapper.markUploadCompleted(organizationId, fileId) != 1) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        objectStorage.finalizeUpload(file.getStorageKey());
        fileScanJobService.enqueue(fileId, actorCasId);
        file.setScanStatus("SCANNING");
        auditService.append(organizationId, actorCasId, "FILE_UPLOAD_COMPLETED", "FILE", fileId,
                "{\"sizeBytes\":" + file.getSizeBytes() + "}");
        return toVO(file);
    }

    public FileDownloadVO createDownload(String fileId) {
        String organizationId = TenantContext.requireOrganizationId();
        String actorCasId = TenantContext.requireCasId();
        FileObject file = fileMapper.findByOrganizationAndId(organizationId, fileId);
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if (!"READY".equals(file.getScanStatus())) {
            throw new BusinessException(BizCode.FILE_NOT_READY, HttpStatus.CONFLICT);
        }
        if (!actorCasId.equals(file.getUploaderCasId()) && !canAccessLinkedInvoice(
                organizationId, actorCasId, fileId)) {
            throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        ObjectStorage.DownloadGrant grant = objectStorage.createDownloadGrant(file.getStorageKey(),
                file.getOriginalName(), file.getContentType());
        return new FileDownloadVO(grant.url(), grant.expiresAt());
    }

    @Transactional
    public FileObjectVO register(String actorCasId, RegisterFileRequest request) {
        String organizationId = TenantContext.requireOrganizationId();
        String name = request.originalName().trim();
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (request.sizeBytes() == null || request.sizeBytes() <= 0) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (request.sizeBytes() > 52_428_800) {
            throw new BusinessException(BizCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }
        validateNameAndType(name, contentType);
        String hash = request.sha256().toLowerCase(Locale.ROOT);
        FileObject existing = fileMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getOrganizationId, organizationId)
                .eq(FileObject::getUploaderCasId, actorCasId)
                .eq(FileObject::getSha256, hash)
                .eq(FileObject::getPurpose, request.purpose())
                .in(FileObject::getScanStatus, "PENDING", "SCANNING", "READY")
                .last("LIMIT 1"));
        if (existing != null) return toVO(existing);
        Instant now = Instant.now();
        String id = UlidGenerator.next();
        FileObject file = FileObject.builder()
                .id(id).organizationId(organizationId).uploaderCasId(actorCasId)
                .storageKey(organizationId + "/" + PATH_DATE.format(now) + "/" + id)
                .originalName(name).contentType(contentType).sizeBytes(request.sizeBytes())
                .sha256(hash).purpose(request.purpose()).scanStatus("PENDING")
                .expiresAt(now.plusSeconds(86_400)).build();
        fileMapper.insert(file);
        auditService.append(organizationId, actorCasId, "FILE_REGISTERED", "FILE", id,
                "{\"purpose\":\"" + request.purpose() + "\"}");
        return toVO(file);
    }

    public FileObjectVO detail(String fileId) {
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        FileObject file = fileMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getOrganizationId, organizationId)
                .eq(FileObject::getId, fileId)
                .eq(FileObject::getUploaderCasId, casId));
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        return toVO(file);
    }

    @Transactional
    public FileObjectVO inspect(String actorCasId, String organizationId, String fileId,
                                InspectFileRequest request) {
        authorizationService.requirePlatformAdmin(actorCasId);
        FileObject file = fileMapper.findByOrganizationAndId(organizationId, fileId);
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if ("READY".equals(file.getScanStatus())) return toVO(file);
        if (!Set.of("PENDING", "SCANNING").contains(file.getScanStatus())) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        String previewId = blankToNull(request.previewFileId());
        if (previewId != null) {
            FileObject preview = fileMapper.findByOrganizationAndId(organizationId, previewId);
            if (preview == null || !"READY".equals(preview.getScanStatus())) {
                throw new BusinessException(BizCode.FILE_NOT_READY, HttpStatus.CONFLICT);
            }
        }
        Instant now = Instant.now();
        Instant readyAt = "READY".equals(request.status()) ? now : null;
        Instant expiresAt = "READY".equals(request.status()) ? null : now.plusSeconds(86_400);
        fileMapper.updateInspection(organizationId, fileId, request.status(), previewId,
                readyAt, expiresAt);
        file.setScanStatus(request.status());
        file.setPreviewFileId(previewId);
        file.setReadyAt(readyAt);
        file.setExpiresAt(expiresAt);
        try (TenantContext.Scope ignored = TenantContext.open(organizationId, actorCasId)) {
            auditService.append(organizationId, actorCasId, "FILE_INSPECTION_RECORDED", "FILE",
                    fileId, "{\"status\":\"" + request.status() + "\"}");
        }
        return toVO(file);
    }

    public FileObject requireReadyOwned(String fileId, String actorCasId,
                                        Set<String> allowedPurposes) {
        String organizationId = TenantContext.requireOrganizationId();
        FileObject file = fileMapper.selectOne(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getOrganizationId, organizationId)
                .eq(FileObject::getId, fileId)
                .eq(FileObject::getUploaderCasId, actorCasId));
        if (file == null) throw new BusinessException(BizCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        if (!"READY".equals(file.getScanStatus())) {
            throw new BusinessException(BizCode.FILE_NOT_READY, HttpStatus.CONFLICT);
        }
        if (!allowedPurposes.contains(file.getPurpose())) {
            throw new BusinessException(BizCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }
        return file;
    }

    public void requireUnused(String fileId) {
        if (fileMapper.isReferenced(TenantContext.requireOrganizationId(), fileId)) {
            throw new BusinessException(BizCode.FILE_ALREADY_USED, HttpStatus.CONFLICT);
        }
    }

    public void markReferenced(String fileId) {
        fileMapper.markReferenced(TenantContext.requireOrganizationId(), fileId);
    }

    private void validateNameAndType(String name, String contentType) {
        if (name.isEmpty() || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (!TYPES.contains(contentType)) {
            throw new BusinessException(BizCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        boolean matches = contentType.equals("application/pdf") && lower.endsWith(".pdf")
                || contentType.equals("application/ofd") && lower.endsWith(".ofd")
                || contentType.equals("image/jpeg") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                || contentType.equals("image/png") && lower.endsWith(".png");
        if (!matches) throw new BusinessException(BizCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
    }

    private boolean canAccessLinkedInvoice(String organizationId, String actorCasId, String fileId) {
        boolean unrestricted = authorizationService.hasPermission("application:review")
                || authorizationService.hasPermission("audit-log:read");
        return fileMapper.findLinkedInvoiceIds(organizationId, fileId).stream()
                .anyMatch(invoiceId -> ledgerMapper.canAccessInvoice(
                        organizationId, actorCasId, unrestricted, invoiceId));
    }

    private BusinessException uploadInvalid() {
        return new BusinessException(BizCode.FILE_UPLOAD_INVALID, HttpStatus.CONFLICT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private FileObjectVO toVO(FileObject file) {
        return new FileObjectVO(file.getId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), file.getSha256(), file.getPurpose(), file.getScanStatus(),
                file.getPreviewFileId(), file.getReadyAt(),
                file.getExpiresAt(), file.getCreatedAt());
    }
}
