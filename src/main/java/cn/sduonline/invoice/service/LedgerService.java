package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.LedgerDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.SavedInvoiceFilter;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.InvoiceTimelineMapper;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.mapper.SavedInvoiceFilterMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LedgerService {
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "updatedAt", "i.updated_at",
            "invoiceDate", "i.invoice_date",
            "faceAmount", "i.face_amount",
            "claimedAmount", "i.claimed_amount",
            "applicantName", "u.name",
            "status", "i.status");
    private static final Set<String> PAPER_STATUSES = Set.of(
            "NOT_REQUIRED", "PENDING_SETUP", "PENDING_DELIVERY", "MEMBER_DECLARED",
            "CLUB_RECEIVED", "RETURNED_TO_MEMBER", "TRANSFERRED_EXTERNAL", "ARCHIVED", "EXCEPTION");
    private static final Set<String> INVOICE_STATUSES = Set.of(
            "DRAFT", "PENDING_RECOGNITION", "SUBMITTED", "IN_REVIEW", "RETURNED",
            "INTERNALLY_APPROVED", "REJECTED", "IN_EXPORT_BATCH", "VOIDED", "ARCHIVED");

    private final LedgerMapper ledgerMapper;
    private final SavedInvoiceFilterMapper savedFilterMapper;
    private final InvoiceTimelineMapper timelineMapper;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public LedgerService(LedgerMapper ledgerMapper,
                         SavedInvoiceFilterMapper savedFilterMapper,
                         InvoiceTimelineMapper timelineMapper,
                         AuthorizationService authorizationService,
                         ObjectMapper objectMapper) {
        this.ledgerMapper = ledgerMapper;
        this.savedFilterMapper = savedFilterMapper;
        this.timelineMapper = timelineMapper;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    public LedgerPageVO invoices(long page, long pageSize, LedgerFilter filter,
                                 String sortBy, String direction) {
        validateFilter(filter);
        String sortColumn = SORT_COLUMNS.get(sortBy == null ? "updatedAt" : sortBy);
        if (sortColumn == null) invalid();
        String sortDirection = direction == null ? "DESC" : direction.toUpperCase();
        if (!Set.of("ASC", "DESC").contains(sortDirection)) invalid();
        String organizationId = TenantContext.requireOrganizationId();
        String actorCasId = TenantContext.requireCasId();
        boolean unrestricted = unrestricted();
        IPage<LedgerInvoiceVO> result = ledgerMapper.findInvoices(new Page<>(page, pageSize),
                organizationId, actorCasId, unrestricted, filter, sortColumn, sortDirection);
        LedgerTotalsVO totals = ledgerMapper.calculateTotals(
                organizationId, actorCasId, unrestricted, filter);
        return new LedgerPageVO(result.getRecords(), page, pageSize, result.getTotal(),
                totals == null ? BigDecimal.ZERO : totals.totalFaceAmount(),
                totals == null ? BigDecimal.ZERO : totals.totalClaimedAmount());
    }

    public List<InvoiceTimelineEventVO> timeline(String invoiceId) {
        String organizationId = TenantContext.requireOrganizationId();
        String actorCasId = TenantContext.requireCasId();
        if (!ledgerMapper.canAccessInvoice(organizationId, actorCasId, unrestricted(), invoiceId)) {
            throw new BusinessException(BizCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return timelineMapper.findForInvoice(organizationId, invoiceId);
    }

    public List<SavedInvoiceFilterVO> savedFilters() {
        String organizationId = TenantContext.requireOrganizationId();
        String owner = TenantContext.requireCasId();
        return savedFilterMapper.selectList(new LambdaQueryWrapper<SavedInvoiceFilter>()
                        .eq(SavedInvoiceFilter::getOrganizationId, organizationId)
                        .eq(SavedInvoiceFilter::getOwnerCasId, owner)
                        .orderByDesc(SavedInvoiceFilter::getIsDefault)
                        .orderByAsc(SavedInvoiceFilter::getName)).stream()
                .map(this::toVO).toList();
    }

    @Transactional
    public SavedInvoiceFilterVO createSavedFilter(CreateSavedFilterRequest request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()
                || request.name().trim().length() > 100 || request.filter() == null) invalid();
        validateFilter(request.filter());
        String organizationId = TenantContext.requireOrganizationId();
        String owner = TenantContext.requireCasId();
        savedFilterMapper.lockOwner(owner);
        requireUniqueName(organizationId, owner, request.name(), null);
        String id = UlidGenerator.next();
        if (request.isDefault()) savedFilterMapper.clearOtherDefaults(organizationId, owner, id);
        SavedInvoiceFilter filter = SavedInvoiceFilter.builder()
                .id(id).organizationId(organizationId).ownerCasId(owner)
                .name(request.name().trim()).filterJson(writeFilter(request.filter()))
                .isDefault(request.isDefault()).version(0L).build();
        try {
            savedFilterMapper.insert(filter);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(BizCode.SAVED_FILTER_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        return toVO(filter);
    }

    @Transactional
    public SavedInvoiceFilterVO updateSavedFilter(String filterId,
                                                   UpdateSavedFilterRequest request) {
        if (request == null || request.version() == null || request.version() < 0) invalid();
        String organizationId = TenantContext.requireOrganizationId();
        String owner = TenantContext.requireCasId();
        savedFilterMapper.lockOwner(owner);
        SavedInvoiceFilter filter = requireOwnedFilter(filterId, organizationId, owner);
        if (filter.getVersion() == null || filter.getVersion() != request.version()) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        if (request.name() != null) {
            if (request.name().trim().isEmpty() || request.name().trim().length() > 100) invalid();
            requireUniqueName(organizationId, owner, request.name(), filterId);
            filter.setName(request.name().trim());
        }
        if (request.filter() != null) {
            validateFilter(request.filter());
            filter.setFilterJson(writeFilter(request.filter()));
        }
        if (request.isDefault() != null) {
            filter.setIsDefault(request.isDefault());
            if (request.isDefault()) {
                savedFilterMapper.clearOtherDefaults(organizationId, owner, filterId);
            }
        }
        filter.setVersion(request.version());
        try {
            if (savedFilterMapper.updateById(filter) != 1) {
                throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(BizCode.SAVED_FILTER_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        filter.setVersion(request.version() + 1);
        return toVO(filter);
    }

    @Transactional
    public void deleteSavedFilter(String filterId, long version) {
        String organizationId = TenantContext.requireOrganizationId();
        String owner = TenantContext.requireCasId();
        requireOwnedFilter(filterId, organizationId, owner);
        if (savedFilterMapper.deleteOwnedVersion(organizationId, owner, filterId, version) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private boolean unrestricted() {
        return authorizationService.hasPermission("application:review")
                || authorizationService.hasPermission("audit-log:read");
    }

    private void validateFilter(LedgerFilter filter) {
        if (filter == null) return;
        if (filter.invoiceDateFrom() != null && filter.invoiceDateTo() != null
                && filter.invoiceDateTo().isBefore(filter.invoiceDateFrom())) invalid();
        if (filter.minClaimedAmount() != null && filter.minClaimedAmount().signum() < 0
                || filter.maxClaimedAmount() != null && filter.maxClaimedAmount().signum() < 0
                || filter.minClaimedAmount() != null && filter.maxClaimedAmount() != null
                && filter.maxClaimedAmount().compareTo(filter.minClaimedAmount()) < 0) invalid();
        if (filter.paperStatus() != null && !PAPER_STATUSES.contains(filter.paperStatus())) invalid();
        if (filter.statuses() != null && (filter.statuses().size() > 20
                || !INVOICE_STATUSES.containsAll(filter.statuses()))) invalid();
        if (filter.keyword() != null && filter.keyword().length() > 100) invalid();
        if (tooLong(filter.projectId(), 26) || tooLong(filter.formId(), 26)
                || tooLong(filter.applicantCasId(), 20)
                || tooLong(filter.expenseCategoryItemId(), 26)
                || tooLong(filter.tagItemId(), 26) || tooLong(filter.invoiceType(), 40)) invalid();
    }

    private SavedInvoiceFilter requireOwnedFilter(String id, String organizationId, String owner) {
        SavedInvoiceFilter filter = savedFilterMapper.selectOne(
                new LambdaQueryWrapper<SavedInvoiceFilter>()
                        .eq(SavedInvoiceFilter::getOrganizationId, organizationId)
                        .eq(SavedInvoiceFilter::getOwnerCasId, owner)
                        .eq(SavedInvoiceFilter::getId, id));
        if (filter == null) {
            throw new BusinessException(BizCode.SAVED_FILTER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return filter;
    }

    private void requireUniqueName(String organizationId, String owner, String name, String exceptId) {
        LambdaQueryWrapper<SavedInvoiceFilter> query = new LambdaQueryWrapper<SavedInvoiceFilter>()
                .eq(SavedInvoiceFilter::getOrganizationId, organizationId)
                .eq(SavedInvoiceFilter::getOwnerCasId, owner)
                .eq(SavedInvoiceFilter::getName, name.trim());
        if (exceptId != null) query.ne(SavedInvoiceFilter::getId, exceptId);
        if (savedFilterMapper.selectCount(query) > 0) {
            throw new BusinessException(BizCode.SAVED_FILTER_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
    }

    private String writeFilter(LedgerFilter filter) {
        try {
            String json = objectMapper.writeValueAsString(filter);
            if (json.length() > 20_000) invalid();
            return json;
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private LedgerFilter readFilter(String json) {
        try {
            return objectMapper.readValue(json, LedgerFilter.class);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SavedInvoiceFilterVO toVO(SavedInvoiceFilter filter) {
        return new SavedInvoiceFilterVO(filter.getId(), filter.getName(),
                readFilter(filter.getFilterJson()), Boolean.TRUE.equals(filter.getIsDefault()),
                filter.getVersion() == null ? 0 : filter.getVersion(),
                filter.getCreatedAt(), filter.getUpdatedAt());
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }

    private boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }
}
