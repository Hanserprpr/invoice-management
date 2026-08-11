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
        return Optional.of(job);
    }

    @Transactional
    public void succeed(String jobId, String resultJson) {
        jobMapper.succeed(jobId, resultJson);
    }

    @Transactional
    public boolean retryOrFail(AsyncJob job, String errorCode, String errorMessage) {
        String safeMessage = truncate(errorMessage == null ? "任务执行失败" : errorMessage, 1000);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            long delay = Math.min(900, 30L << Math.min(job.getAttemptCount() - 1, 4));
            jobMapper.retry(job.getId(), truncate(errorCode, 80), safeMessage,
                    Instant.now().plus(delay, ChronoUnit.SECONDS));
            return true;
        }
        jobMapper.fail(job.getId(), truncate(errorCode, 80), safeMessage);
        return false;
    }

    public List<AsyncJob> findStale(String jobType, Instant staleBefore, int limit) {
        return jobMapper.findStaleRunning(jobType, staleBefore, limit);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
