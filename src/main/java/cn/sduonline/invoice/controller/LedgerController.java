package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.LedgerDtos.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.service.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * 按条件分页查询发票台账。
     */
    @GetMapping("/invoices")
    public Result<LedgerPageVO> invoices(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String formId,
            @RequestParam(required = false) String applicantCasId,
            @RequestParam(required = false) LocalDate invoiceDateFrom,
            @RequestParam(required = false) LocalDate invoiceDateTo,
            @RequestParam(required = false) BigDecimal minClaimedAmount,
            @RequestParam(required = false) BigDecimal maxClaimedAmount,
            @RequestParam(required = false) String expenseCategoryItemId,
            @RequestParam(required = false) Set<String> statuses,
            @RequestParam(required = false) String tagItemId,
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) String paperStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        LedgerFilter filter = new LedgerFilter(projectId, formId, applicantCasId,
                invoiceDateFrom, invoiceDateTo, minClaimedAmount, maxClaimedAmount,
                expenseCategoryItemId, statuses, tagItemId, invoiceType, paperStatus, keyword);
        return Result.ok(ledgerService.invoices(page, pageSize, filter, sortBy, direction));
    }

    /**
     * 获取指定发票的流转时间线。
     */
    @GetMapping("/invoices/{invoiceId}/timeline")
    public Result<List<InvoiceTimelineEventVO>> timeline(@PathVariable String invoiceId) {
        return Result.ok(ledgerService.timeline(invoiceId));
    }

    /**
     * 获取当前用户保存的台账筛选器。
     */
    @GetMapping("/saved-filters")
    public Result<List<SavedInvoiceFilterVO>> savedFilters() {
        return Result.ok(ledgerService.savedFilters());
    }

    /**
     * 保存新的台账筛选器。
     */
    @PostMapping("/saved-filters")
    public ResponseEntity<Result<SavedInvoiceFilterVO>> createSavedFilter(
            @Valid @RequestBody CreateSavedFilterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(ledgerService.createSavedFilter(request)));
    }

    /**
     * 更新指定的台账筛选器。
     */
    @PatchMapping("/saved-filters/{filterId}")
    public Result<SavedInvoiceFilterVO> updateSavedFilter(
            @PathVariable String filterId,
            @Valid @RequestBody UpdateSavedFilterRequest request) {
        return Result.ok(ledgerService.updateSavedFilter(filterId, request));
    }

    /**
     * 删除指定的台账筛选器。
     */
    @DeleteMapping("/saved-filters/{filterId}")
    public Result<Void> deleteSavedFilter(@PathVariable String filterId,
                                          @RequestParam @Min(0) long version) {
        ledgerService.deleteSavedFilter(filterId, version);
        return Result.ok();
    }
}
