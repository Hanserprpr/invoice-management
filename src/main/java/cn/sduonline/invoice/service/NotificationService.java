package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.Notification;
import cn.sduonline.invoice.data.vo.NotificationVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.NotificationMapper;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class NotificationService {
    private final NotificationMapper mapper;
    private final AsyncJobMapper jobMapper;

    public NotificationService(NotificationMapper mapper, AsyncJobMapper jobMapper) {
        this.mapper = mapper;
        this.jobMapper = jobMapper;
    }

    public PageResult<NotificationVO> inbox(long page, long pageSize, String status) {
        if (status != null && !Set.of("UNREAD", "READ", "DISMISSED").contains(status)) invalid();
        var result = mapper.findInbox(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                TenantContext.requireOrganizationId(), TenantContext.requireCasId(), status);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                page, pageSize, result.getTotal());
    }

    public NotificationVO read(String id) { return change(id, "READ"); }

    public NotificationVO dismiss(String id) { return change(id, "DISMISSED"); }

    public void createDeduplicated(String organizationId, String recipientCasId, String type,
                                   String title, String content, String targetType,
                                   String targetId, String deduplicationKey) {
        try {
            String id = UlidGenerator.next();
            mapper.insert(Notification.builder().id(id).organizationId(organizationId)
                    .recipientCasId(recipientCasId).notificationType(type).title(title)
                    .content(content).targetType(targetType).targetId(targetId)
                    .deduplicationKey(deduplicationKey).status("UNREAD").build());
            jobMapper.insert(AsyncJob.builder().id(UlidGenerator.next()).organizationId(organizationId)
                    .jobType("NOTIFICATION_DELIVERY").targetType("NOTIFICATION").targetId(id)
                    .status("PENDING").progress(0).attemptCount(0).maxAttempts(3)
                    .createdByCasId(recipientCasId).build());
        } catch (DuplicateKeyException ignored) {
            // At-least-once callers intentionally converge on one inbox item.
        }
    }

    private NotificationVO change(String id, String status) {
        Instant readAt = "READ".equals(status) ? Instant.now() : null;
        if (mapper.changeStatus(TenantContext.requireOrganizationId(), TenantContext.requireCasId(),
                id, status, readAt) != 1) {
            throw new BusinessException(BizCode.NOTIFICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        Notification notification = mapper.selectById(id);
        return toVO(notification);
    }

    private NotificationVO toVO(Notification row) {
        return new NotificationVO(row.getId(), row.getNotificationType(), row.getTitle(),
                row.getContent(), row.getTargetType(), row.getTargetId(), row.getStatus(),
                row.getReadAt(), row.getCreatedAt());
    }

    private void invalid() { throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST); }
}
