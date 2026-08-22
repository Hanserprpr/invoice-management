package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;

@Service
public class AsyncJobClaimService {
    public enum FailureOutcome {
        RETRY_SCHEDULED,
        FAILED,
        LEASE_LOST
    }

    private final AsyncJobMapper jobMapper;

    public AsyncJobClaimService(AsyncJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @Transactional
    public Optional<AsyncJob> claim(String jobType) {
        AsyncJob job = jobMapper.lockNextPending(jobType);
        if (job == null || jobMapper.claim(job.getId()) != 1) return Optional.empty();
        job.setStatus("RUNNING");
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setLeaseVersion((job.getLeaseVersion() == null ? 0 : job.getLeaseVersion()) + 1);
        return Optional.of(job);
    }

    @Transactional
    public void succeed(AsyncJob job, String resultJson) {
        if (jobMapper.succeed(job.getId(), requireLease(job), resultJson) != 1) {
            throw new IllegalStateException("ASYNC_JOB_LEASE_LOST");
        }
    }

    @Transactional
    public FailureOutcome retryOrFail(AsyncJob job, String errorCode, String errorMessage) {
        String safeMessage = truncate(errorMessage == null ? "任务执行失败" : errorMessage, 1000);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            long delay = Math.min(900, 30L << Math.min(job.getAttemptCount() - 1, 4));
            int updated = jobMapper.retry(job.getId(), requireLease(job), truncate(errorCode, 80),
                    safeMessage, Instant.now().plus(delay, ChronoUnit.SECONDS));
            return updated == 1 ? FailureOutcome.RETRY_SCHEDULED : FailureOutcome.LEASE_LOST;
        }
        int updated = jobMapper.fail(job.getId(), requireLease(job), truncate(errorCode, 80),
                safeMessage);
        return updated == 1 ? FailureOutcome.FAILED : FailureOutcome.LEASE_LOST;
    }

    public List<AsyncJob> findStale(String jobType, Instant staleBefore, int limit) {
        return jobMapper.findStaleRunning(jobType, staleBefore, limit);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private long requireLease(AsyncJob job) {
        if (job.getLeaseVersion() == null) {
            throw new IllegalStateException("ASYNC_JOB_LEASE_MISSING");
        }
        return job.getLeaseVersion();
    }
}
