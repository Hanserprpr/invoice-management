package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.Notification;
import cn.sduonline.invoice.mapper.NotificationMapper;
import cn.sduonline.invoice.notification.NotificationDeliveryAdapter;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class NotificationDeliveryWorker {
    private static final String JOB_TYPE = "NOTIFICATION_DELIVERY";
    private final AsyncJobClaimService claimService;
    private final NotificationMapper mapper;
    private final NotificationDeliveryAdapter adapter;
    private final ObjectMapper objectMapper;

    public NotificationDeliveryWorker(AsyncJobClaimService claimService, NotificationMapper mapper,
                                      NotificationDeliveryAdapter adapter, ObjectMapper objectMapper) {
        this.claimService = claimService;
        this.mapper = mapper;
        this.adapter = adapter;
        this.objectMapper = objectMapper;
    }

    public boolean processNext() {
        for (AsyncJob stale : claimService.findStale(JOB_TYPE,
                Instant.now().minus(5, ChronoUnit.MINUTES), 20)) {
            claimService.retryOrFail(stale, "WORKER_LEASE_EXPIRED", "通知投递节点超时，已重新调度");
        }
        var claimed = claimService.claim(JOB_TYPE);
        if (claimed.isEmpty()) return false;
        AsyncJob job = claimed.get();
        try {
            try (TenantContext.Scope ignored = TenantContext.open(job.getOrganizationId(),
                    job.getCreatedByCasId())) {
                Notification notification = mapper.selectById(job.getTargetId());
                if (notification == null) throw new IllegalStateException("通知不存在");
                var result = adapter.deliver(notification);
                claimService.succeed(job, objectMapper.writeValueAsString(
                        Map.of("outcome", result.outcome())));
            }
        } catch (Exception exception) {
            claimService.retryOrFail(job, "NOTIFICATION_DELIVERY_FAILED",
                    exception.getMessage() == null ? "通知投递失败" : exception.getMessage());
        }
        return true;
    }
}
