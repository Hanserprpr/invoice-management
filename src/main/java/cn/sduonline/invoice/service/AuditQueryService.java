package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.vo.AuditLogVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.mapper.AuditLogMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
    private final AuditLogMapper mapper;
    private final AuthorizationService authorizationService;

    public AuditQueryService(AuditLogMapper mapper, AuthorizationService authorizationService) {
        this.mapper = mapper;
        this.authorizationService = authorizationService;
    }

    public PageResult<AuditLogVO> list(long page, long pageSize, String actorCasId,
                                       String action, String objectType, String objectId) {
        authorizationService.requirePermission("audit-log:read");
        var result = mapper.findPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                TenantContext.requireOrganizationId(), clean(actorCasId), clean(action),
                clean(objectType), clean(objectId));
        return new PageResult<>(result.getRecords().stream().map(row -> new AuditLogVO(
                row.getId(), row.getActorCasId(), row.getAction(), row.getObjectType(),
                row.getObjectId(), row.getChangeSummaryJson(), row.getRequestId(),
                row.getCreatedAt())).toList(), page, pageSize, result.getTotal());
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
