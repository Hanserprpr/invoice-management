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
     *
     * <p>台账主查询，分页记录与金额合计使用同一套租户、权限和业务筛选条件，合计不受当前页大小影响。
     *
     * <ul>
     *   <li>权限：普通社员只能看到本人发票；社团审核或审计角色可看授权范围；项目负责人和有 `VIEW`/`REVIEW`/`MANAGE` 范围的成员只能看对应项目。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`，`pageSize` 默认 `20`、上限 `100`；`projectId`、`formId`、`applicantCasId`、`invoiceDateFrom`、`invoiceDateTo`、`minClaimedAmount`、`maxClaimedAmount`、`expenseCategoryItemId`、`statuses`、`tagItemId`、`invoiceType`、`paperStatus`、`keyword` 均可选。</li>
     *   <li>排序：`sortBy` 取值 `updatedAt`（默认）、`invoiceDate`、`faceAmount`、`claimedAmount`、`applicantName`、`status`；`direction` 取值 `ASC`、`DESC`（默认）。</li>
     *   <li>成功：`200`，`data` 含分页记录以及 `totalFaceAmount`、`totalClaimedAmount`。</li>
     *   <li>常见错误：`10000` 排序字段、状态或筛选值非法。</li>
     * </ul>
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
     *
     * <p>把提交、审核、纸票、导出等事件聚合成一条时间线。权限判定与台账列表完全一致，知道发票 ID 也绕不过数据范围。
     *
     * <ul>
     *   <li>权限：与台账列表相同的数据范围。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50000` 发票不存在或不在可见范围内。</li>
     * </ul>
     */
    @GetMapping("/invoices/{invoiceId}/timeline")
    public Result<List<InvoiceTimelineEventVO>> timeline(@PathVariable String invoiceId) {
        return Result.ok(ledgerService.timeline(invoiceId));
    }

    /**
     * 获取当前用户保存的台账筛选器。
     *
     * <p>返回本人保存的筛选条件，默认项排在前面。筛选器是私有的，不会在成员之间共享。
     *
     * <ul>
     *   <li>权限：仅本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     * </ul>
     */
    @GetMapping("/saved-filters")
    public Result<List<SavedInvoiceFilterVO>> savedFilters() {
        return Result.ok(ledgerService.savedFilters());
    }

    /**
     * 保存新的台账筛选器。
     *
     * <p>把一组台账筛选条件存成命名快捷方式。设为默认时会自动取消本人原来的默认项。
     *
     * <ul>
     *   <li>权限：仅本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`name`（不超过 100 字）与 `filter` 必填；`isDefault` 可选。</li>
     *   <li>成功：`201`。</li>
     *   <li>常见错误：`62001` 本人已有同名筛选；`10000` 筛选条件取值非法。</li>
     * </ul>
     */
    @PostMapping("/saved-filters")
    public ResponseEntity<Result<SavedInvoiceFilterVO>> createSavedFilter(
            @Valid @RequestBody CreateSavedFilterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(ledgerService.createSavedFilter(request)));
    }

    /**
     * 更新指定的台账筛选器。
     *
     * <p>修改本人筛选器的名称、条件或默认标记。只提交需要改动的字段，`filter` 一旦提交即为整体替换。
     *
     * <ul>
     *   <li>权限：仅筛选器所有者本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`name`、`filter`、`isDefault` 可选。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`62000` 筛选不存在或不属于本人；`62001` 重名；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/saved-filters/{filterId}")
    public Result<SavedInvoiceFilterVO> updateSavedFilter(
            @PathVariable String filterId,
            @Valid @RequestBody UpdateSavedFilterRequest request) {
        return Result.ok(ledgerService.updateSavedFilter(filterId, request));
    }

    /**
     * 删除指定的台账筛选器。
     *
     * <p>永久删除本人的一个筛选器。删除只影响快捷方式，不影响任何发票数据。
     *
     * <ul>
     *   <li>权限：仅筛选器所有者本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求参数：查询参数 `version` 必填。</li>
     *   <li>成功：`200`，`data` 为空。</li>
     *   <li>常见错误：`62000` 筛选不存在或不属于本人；`10007` 版本冲突。</li>
     * </ul>
     */
    @DeleteMapping("/saved-filters/{filterId}")
    public Result<Void> deleteSavedFilter(@PathVariable String filterId,
                                          @RequestParam @Min(0) long version) {
        ledgerService.deleteSavedFilter(filterId, version);
        return Result.ok();
    }
}
