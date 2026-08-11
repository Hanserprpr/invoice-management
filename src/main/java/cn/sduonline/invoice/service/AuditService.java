package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AuditLog;
import cn.sduonline.invoice.mapper.AuditLogMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void append(String organizationId, String actorCasId, String action,
                       String objectType, String objectId, String summaryJson) {
        HttpServletRequest request = currentRequest();
        String requestId = request == null ? UUID.randomUUID().toString()
                : defaultIfBlank(request.getHeader("X-Request-Id"), UUID.randomUUID().toString());
        auditLogMapper.insert(AuditLog.builder()
                .id(UlidGenerator.next())
                .organizationId(organizationId)
                .actorCasId(actorCasId)
                .action(action)
                .objectType(objectType)
                .objectId(objectId)
                .changeSummaryJson(summaryJson)
                .requestId(requestId)
                .ipAddress(request == null ? null : request.getRemoteAddr())
                .userAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 500))
                .build());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : truncate(value, 100);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
