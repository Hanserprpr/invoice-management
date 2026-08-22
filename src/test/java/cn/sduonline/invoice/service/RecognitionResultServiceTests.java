package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.data.po.RecognitionSuggestion;
import cn.sduonline.invoice.mapper.AsyncJobMapper;
import cn.sduonline.invoice.mapper.RecognitionInvoiceMapper;
import cn.sduonline.invoice.mapper.RecognitionSuggestionMapper;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter.RecognitionResult;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter.SuggestedField;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecognitionResultServiceTests {

    @Test
    void resultFromReplacedFileIsDiscardedWithoutCreatingSuggestions() {
        RecognitionSuggestionMapper suggestionMapper = mock(RecognitionSuggestionMapper.class);
        AsyncJobMapper jobMapper = mock(AsyncJobMapper.class);
        RecognitionInvoiceMapper invoiceMapper = mock(RecognitionInvoiceMapper.class);
        AsyncJobClaimService claimService = mock(AsyncJobClaimService.class);
        RecognitionResultService service = new RecognitionResultService(
                suggestionMapper, jobMapper, invoiceMapper, claimService, new ObjectMapper());
        AsyncJob claimed = AsyncJob.builder().id("job-1").organizationId("org-1")
                .targetId("invoice-1").status("RUNNING").leaseVersion(2L).build();
        AsyncJob current = AsyncJob.builder().id("job-1").status("RUNNING")
                .leaseVersion(2L).build();
        Invoice invoice = Invoice.builder().id("invoice-1").organizationId("org-1")
                .currentFileId("new-file").status("PENDING_RECOGNITION").build();
        when(invoiceMapper.lockById("org-1", "invoice-1")).thenReturn(invoice);
        when(jobMapper.lockById("job-1")).thenReturn(current);
        when(invoiceMapper.restoreDraftIfPending("org-1", "invoice-1")).thenReturn(1);
        when(jobMapper.succeed(eq("job-1"), eq(2L), any())).thenReturn(1);

        service.persist(claimed, "old-file",
                new RecognitionResult("raw", null, Map.of(
                        "sellerName", new SuggestedField("旧供应商", null)), true));

        verify(invoiceMapper).restoreDraftIfPending("org-1", "invoice-1");
        verify(suggestionMapper, never()).insert(any(RecognitionSuggestion.class));
        verify(jobMapper).succeed(eq("job-1"), eq(2L), argThat(result ->
                result.contains("DISCARDED_FILE_CHANGED")
                        && result.contains("old-file") && result.contains("new-file")));
    }
}
