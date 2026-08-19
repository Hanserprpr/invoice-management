package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.mapper.InvoiceMapper;
import cn.sduonline.invoice.mapper.LedgerMapper;
import cn.sduonline.invoice.mapper.RecognitionInvoiceMapper;
import cn.sduonline.invoice.mapper.RecognitionSuggestionMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecognitionServiceTests {

    @Test
    void duplicateInsertUsesCurrentReadToReturnTheWinningActiveJob() {
        Fixture fixture = new Fixture();
        Invoice draft = invoice("DRAFT");
        AsyncJob winner = activeJob("winner-job");
        when(fixture.recognitionInvoiceMapper.findOwnedById("org-1", "invoice-1", "owner"))
                .thenReturn(draft);
        when(fixture.recognitionInvoiceMapper.lockById("org-1", "invoice-1"))
                .thenReturn(draft);
        when(fixture.jobMapper.findActiveForTargetForUpdate(
                "org-1", RecognitionWorker.JOB_TYPE, "INVOICE", "invoice-1"))
                .thenReturn(null, winner);
        when(fixture.jobMapper.insert(any(AsyncJob.class)))
                .thenThrow(new DuplicateKeyException("active target already claimed"));
        when(fixture.recognitionInvoiceMapper.markPendingIfDraft("org-1", "invoice-1"))
                .thenReturn(1);

        try (TenantContext.Scope ignored = TenantContext.open("org-1", "owner")) {
            assertThat(fixture.service.start("invoice-1", "owner").id()).isEqualTo("winner-job");
        }
    }

    @Test
    void restartingAQueuedRetryMovesTheRestoredDraftBackToPending() {
        Fixture fixture = new Fixture();
        Invoice draft = invoice("DRAFT");
        AsyncJob active = activeJob("retry-job");
        when(fixture.recognitionInvoiceMapper.findOwnedById("org-1", "invoice-1", "owner"))
                .thenReturn(draft);
        when(fixture.jobMapper.findActiveForTargetForUpdate(
                "org-1", RecognitionWorker.JOB_TYPE, "INVOICE", "invoice-1"))
                .thenReturn(active);
        when(fixture.recognitionInvoiceMapper.lockById("org-1", "invoice-1"))
                .thenReturn(draft);
        when(fixture.recognitionInvoiceMapper.markPendingIfDraft("org-1", "invoice-1"))
                .thenReturn(1);

        try (TenantContext.Scope ignored = TenantContext.open("org-1", "owner")) {
            assertThat(fixture.service.start("invoice-1", "owner").id()).isEqualTo("retry-job");
        }

        verify(fixture.recognitionInvoiceMapper).markPendingIfDraft("org-1", "invoice-1");
    }

    private static Invoice invoice(String status) {
        return Invoice.builder().id("invoice-1").organizationId("org-1")
                .applicationId("application-1").status(status).version(0L).build();
    }

    private static AsyncJob activeJob(String id) {
        return AsyncJob.builder().id(id).organizationId("org-1")
                .jobType(RecognitionWorker.JOB_TYPE).targetType("INVOICE").targetId("invoice-1")
                .status("PENDING").attemptCount(1).maxAttempts(3).build();
    }

    private static final class Fixture {
        private final InvoiceMapper invoiceMapper = mock(InvoiceMapper.class);
        private final RecognitionInvoiceMapper recognitionInvoiceMapper =
                mock(RecognitionInvoiceMapper.class);
        private final AsyncJobMapper jobMapper = mock(AsyncJobMapper.class);
        private final RecognitionSuggestionMapper suggestionMapper =
                mock(RecognitionSuggestionMapper.class);
        private final LedgerMapper ledgerMapper = mock(LedgerMapper.class);
        private final AuthorizationService authorizationService = mock(AuthorizationService.class);
        private final AuditService auditService = mock(AuditService.class);
        private final ObjectMapper objectMapper = mock(ObjectMapper.class);
        private final RecognitionService service = new RecognitionService(
                invoiceMapper, recognitionInvoiceMapper, jobMapper, suggestionMapper,
                ledgerMapper, authorizationService, auditService, objectMapper);
    }
}
