package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.ExportBatch;
import cn.sduonline.invoice.data.po.InvoiceExportReservation;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.ApplicationMapper;
import cn.sduonline.invoice.mapper.ExportBatchInvoiceMapper;
import cn.sduonline.invoice.mapper.ExportBatchMapper;
import cn.sduonline.invoice.mapper.InvoiceExportReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ExportLifecycleService {
    public enum Action {
        CANCEL,
        PENDING_EXTERNAL,
        EXTERNAL_PROCESSING,
        COMPLETE,
        FINAL_ARCHIVE
    }

    private final ExportBatchMapper batchMapper;
    private final ExportBatchInvoiceMapper itemMapper;
    private final InvoiceExportReservationMapper reservationMapper;
    private final ApplicationMapper applicationMapper;

    public ExportLifecycleService(ExportBatchMapper batchMapper,
                                  ExportBatchInvoiceMapper itemMapper,
                                  InvoiceExportReservationMapper reservationMapper,
                                  ApplicationMapper applicationMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.reservationMapper = reservationMapper;
        this.applicationMapper = applicationMapper;
    }

    @Transactional
    public void reserve(ExportBatch batch, List<String> invoiceIds) {
        try {
            for (String invoiceId : invoiceIds) {
                reservationMapper.insert(InvoiceExportReservation.builder()
                        .invoiceId(invoiceId).organizationId(batch.getOrganizationId())
                        .batchId(batch.getId()).build());
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(BizCode.EXPORT_INVOICE_RESERVED, HttpStatus.CONFLICT);
        }
        requireCount(itemMapper.reserveInvoices(batch.getOrganizationId(), invoiceIds),
                invoiceIds.size());
    }

    @Transactional
    public void supersede(ExportBatch previous, String newBatchId, long expectedVersion) {
        requireVersion(previous, expectedVersion);
        requireState(Set.of("GENERATED", "EXPORTED").contains(previous.getStatus()));
        int expectedCount = previous.getInvoiceCount();
        requireCount(itemMapper.countActiveInvoices(previous.getOrganizationId(), previous.getId()),
                expectedCount);
        requireCount(reservationMapper.transferBatch(previous.getOrganizationId(), previous.getId(),
                newBatchId), expectedCount);
        previous.setStatus("ARCHIVED");
        previous.setArchivedAt(Instant.now());
        updateBatch(previous, expectedVersion);
    }

    @Transactional
    public void transition(ExportBatch batch, long expectedVersion, Action action) {
        requireVersion(batch, expectedVersion);
        switch (action) {
            case CANCEL -> cancel(batch, expectedVersion);
            case PENDING_EXTERNAL -> pendingExternal(batch, expectedVersion);
            case EXTERNAL_PROCESSING -> externalProcessing(batch, expectedVersion);
            case COMPLETE -> complete(batch, expectedVersion);
            case FINAL_ARCHIVE -> finalArchive(batch, expectedVersion);
        }
    }

    private void cancel(ExportBatch batch, long expectedVersion) {
        requireState(Set.of("DRAFT", "GENERATED", "EXPORTED", "EXTERNAL_PROCESSING")
                .contains(batch.getStatus()));
        batch.setStatus("CANCELLED");
        batch.setCancelledAt(Instant.now());
        updateBatch(batch, expectedVersion);
        int expectedCount = batch.getInvoiceCount();
        requireCount(itemMapper.restoreInvoices(batch.getOrganizationId(), batch.getId()),
                expectedCount);
        requireCount(reservationMapper.deleteForBatch(batch.getOrganizationId(), batch.getId()),
                expectedCount);
    }

    private void pendingExternal(ExportBatch batch, long expectedVersion) {
        requireState(Set.of("GENERATED", "EXPORTED", "EXTERNAL_PROCESSING")
                .contains(batch.getStatus()));
        updateBatch(batch, expectedVersion);
    }

    private void externalProcessing(ExportBatch batch, long expectedVersion) {
        requireState(Set.of("EXPORTED", "EXTERNAL_PROCESSING").contains(batch.getStatus()));
        batch.setStatus("EXTERNAL_PROCESSING");
        updateBatch(batch, expectedVersion);
    }

    private void complete(ExportBatch batch, long expectedVersion) {
        requireState(Set.of("EXPORTED", "EXTERNAL_PROCESSING").contains(batch.getStatus()));
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(Instant.now());
        updateBatch(batch, expectedVersion);
    }

    private void finalArchive(ExportBatch batch, long expectedVersion) {
        requireState("COMPLETED".equals(batch.getStatus()));
        batch.setStatus("ARCHIVED");
        batch.setArchivedAt(Instant.now());
        updateBatch(batch, expectedVersion);
        requireCount(itemMapper.archiveInvoices(batch.getOrganizationId(), batch.getId()),
                batch.getInvoiceCount());
        for (String applicationId : itemMapper.findApplicationIdsForBatch(
                batch.getOrganizationId(), batch.getId())) {
            requireCount(applicationMapper.refreshDerivedStatus(batch.getOrganizationId(),
                    applicationId), 1);
        }
    }

    private void updateBatch(ExportBatch batch, long expectedVersion) {
        batch.setVersion(expectedVersion);
        if (batchMapper.updateById(batch) != 1) conflict();
        batch.setVersion(expectedVersion + 1);
    }

    private void requireVersion(ExportBatch batch, long expectedVersion) {
        if (batch.getVersion() == null || batch.getVersion() != expectedVersion) conflict();
    }

    private void requireState(boolean allowed) {
        if (!allowed) {
            throw new BusinessException(BizCode.EXPORT_BATCH_STATE_NOT_ALLOWED,
                    HttpStatus.CONFLICT);
        }
    }

    private void requireCount(int actual, int expected) {
        if (actual != expected) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void conflict() {
        throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
    }
}
