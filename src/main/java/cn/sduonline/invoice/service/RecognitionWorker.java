package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.AsyncJob;
import cn.sduonline.invoice.data.po.FileObject;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.mapper.FileObjectMapper;
import cn.sduonline.invoice.mapper.InvoiceMapper;
import cn.sduonline.invoice.recognition.InvoiceRecognitionAdapter;
import cn.sduonline.invoice.storage.ObjectStorage;
import cn.sduonline.invoice.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class RecognitionWorker {
    public static final String JOB_TYPE = "INVOICE_RECOGNITION";
    private final AsyncJobClaimService claimService;
    private final InvoiceMapper invoiceMapper;
    private final FileObjectMapper fileMapper;
    private final ObjectStorage objectStorage;
    private final InvoiceRecognitionAdapter recognitionAdapter;
    private final RecognitionResultService resultService;

    public RecognitionWorker(AsyncJobClaimService claimService, InvoiceMapper invoiceMapper,
                             FileObjectMapper fileMapper, ObjectStorage objectStorage,
                             InvoiceRecognitionAdapter recognitionAdapter,
                             RecognitionResultService resultService) {
        this.claimService = claimService;
        this.invoiceMapper = invoiceMapper;
        this.fileMapper = fileMapper;
        this.objectStorage = objectStorage;
        this.recognitionAdapter = recognitionAdapter;
        this.resultService = resultService;
    }

    public boolean processNext() {
        for (AsyncJob stale : claimService.findStale(JOB_TYPE,
                Instant.now().minusSeconds(300), 100)) {
            resultService.recordFailure(stale, "WORKER_LEASE_EXPIRED", "识别节点超时，已重新调度");
        }
        var claimed = claimService.claim(JOB_TYPE);
        if (claimed.isEmpty()) return false;
        AsyncJob job = claimed.get();
        try (TenantContext.Scope ignored = TenantContext.open(
                job.getOrganizationId(), job.getCreatedByCasId())) {
            process(job);
        } catch (Exception exception) {
            resultService.recordFailure(job, errorCode(exception), safeMessage(exception));
        }
        return true;
    }

    private void process(AsyncJob job) throws Exception {
        Invoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrganizationId, job.getOrganizationId())
                .eq(Invoice::getId, job.getTargetId()));
        if (invoice == null) throw new IllegalStateException("INVOICE_NOT_FOUND");
        FileObject file = fileMapper.findByOrganizationAndId(
                job.getOrganizationId(), invoice.getCurrentFileId());
        if (file == null || !"READY".equals(file.getScanStatus())) {
            throw new IllegalStateException("FILE_NOT_READY");
        }
        Path temp = Files.createTempFile("invoice-recognition-", ".bin");
        try {
            objectStorage.downloadTo(file.getStorageKey(), temp);
            resultService.persist(job, recognitionAdapter.recognize(temp, file.getContentType()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String errorCode(Exception exception) {
        return exception instanceof cn.sduonline.invoice.exception.BusinessException business
                ? business.getBizCode().name() : exception.getClass().getSimpleName();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "识别任务失败" : message;
    }
}
