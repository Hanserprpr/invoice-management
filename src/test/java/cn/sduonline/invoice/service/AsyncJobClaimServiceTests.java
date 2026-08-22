package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncJobClaimServiceTests {

    @Test
    void claimReturnsTheIncrementedLeaseVersion() {
        AsyncJobMapper mapper = mock(AsyncJobMapper.class);
        AsyncJob queued = job(4L);
        when(mapper.lockNextPending("TEST")).thenReturn(queued);
        when(mapper.claim(queued.getId())).thenReturn(1);

        AsyncJob claimed = new AsyncJobClaimService(mapper).claim("TEST").orElseThrow();

        assertThat(claimed.getStatus()).isEqualTo("RUNNING");
        assertThat(claimed.getLeaseVersion()).isEqualTo(5L);
    }

    @Test
    void staleLeaseCannotRetryOrCompleteANewerAttempt() {
        AsyncJobMapper mapper = mock(AsyncJobMapper.class);
        AsyncJob stale = job(1L);
        stale.setStatus("RUNNING");
        stale.setAttemptCount(1);
        when(mapper.retry(eq(stale.getId()), eq(1L), any(), any(), any())).thenReturn(0);
        when(mapper.succeed(stale.getId(), 1L, "{}" )).thenReturn(0);
        AsyncJobClaimService service = new AsyncJobClaimService(mapper);

        assertThat(service.retryOrFail(stale, "TIMEOUT", "expired"))
                .isEqualTo(AsyncJobClaimService.FailureOutcome.LEASE_LOST);
        assertThatThrownBy(() -> service.succeed(stale, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ASYNC_JOB_LEASE_LOST");

        verify(mapper).retry(eq(stale.getId()), eq(1L), eq("TIMEOUT"), eq("expired"), any());
    }

    private AsyncJob job(long leaseVersion) {
        return AsyncJob.builder().id("job-1").organizationId("org-1").jobType("TEST")
                .targetType("TEST").targetId("target-1").status("PENDING")
                .attemptCount(0).maxAttempts(3).leaseVersion(leaseVersion).build();
    }
}
