package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.stereotype.Service;

@Service
public class FileScanJobService {
    private final AsyncJobMapper jobMapper;

    public FileScanJobService(AsyncJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    public String enqueue(String fileId, String actorCasId) {
        String id = UlidGenerator.next();
        jobMapper.insert(AsyncJob.builder()
                .id(id)
                .organizationId(TenantContext.requireOrganizationId())
                .jobType(FileSecurityScanWorker.JOB_TYPE)
                .targetType("FILE")
                .targetId(fileId)
                .status("PENDING")
                .progress(0)
                .attemptCount(0)
                .maxAttempts(3)
                .createdByCasId(actorCasId)
                .build());
        return id;
    }
}
