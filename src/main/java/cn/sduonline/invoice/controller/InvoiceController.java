package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.InvoiceDtos.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 获取指定申报下的发票列表。
     *
     * <p>按创建时间升序返回该申请下的全部发票（含已作废），供填报页展示和提交前自查。
     *
     * <ul>
     *   <li>权限：申报人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`42000` 申请不存在或不属于本人。</li>
     * </ul>
     */
    @GetMapping("/applications/{applicationId}/invoices")
    public Result<List<InvoiceVO>> list(@PathVariable String applicationId) {
        return Result.ok(invoiceService.listForApplication(applicationId));
    }

    /**
     * 在指定申报下创建发票。
     *
     * <p>登记一张发票草稿并绑定其原文件。原文件必须是本人上传、用途为 `INVOICE_ORIGINAL`、已通过安全检测且尚未被其他记录引用的文件。
     *
     * <ul>
     *   <li>权限：申报人本人，且申请处于 `DRAFT` 或 `RETURNED`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`invoiceType`、`faceAmount`、`claimedAmount`、`fileId` 必填；票号、日期、购销方、`expenseCategoryItemId` 可选。</li>
     *   <li>金额约束：`claimedAmount` 必须大于 `0` 且不超过 `faceAmount`，两者最多两位小数、整数部分不超过 10 位。</li>
     *   <li>成功：`201`，`data.status` 为 `DRAFT`。</li>
     *   <li>常见错误：`50002` 金额非法；`80000` 文件未就绪；`80008` 文件已被其他记录引用；`53002` 费用类别字典项不存在；`42001` 申请状态不允许。</li>
     * </ul>
     */
    @PostMapping("/applications/{applicationId}/invoices")
    public ResponseEntity<Result<InvoiceVO>> create(@PathVariable String applicationId,
                                                     Authentication authentication,
                                                     @Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(
                invoiceService.create(applicationId, authentication.getName(), request)));
    }

    /**
     * 获取指定发票的详情。
     *
     * <p>以申报人视角读取单张发票的全部字段、当前文件与乐观锁 `version`。审核侧请改用 `GET /api/reviews/invoices/{invoiceId}`。
     *
     * <ul>
     *   <li>权限：申报人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50000` 发票不存在或不属于本人。</li>
     * </ul>
     */
    @GetMapping("/invoices/{invoiceId}")
    public Result<InvoiceVO> detail(@PathVariable String invoiceId) {
        return Result.ok(invoiceService.detail(invoiceId));
    }

    /**
     * 更新指定发票的信息。
     *
     * <p>修改发票字段。发票被退回后，只允许改动审核人在退回时指定的字段，其余字段会被拒绝。
     *
     * <ul>
     *   <li>权限：申报人本人，发票处于 `DRAFT` 或 `RETURNED`，且所属申请可编辑。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；其余字段只提交需要修改的部分；置空可空字段请把字段名放进 `clearFields`。</li>
     *   <li>成功：`200`，`data.version` 已自增。</li>
     *   <li>常见错误：`50001` 发票状态不允许修改；`60006` 该字段不在退回时允许修改的范围内；`50002` 金额非法；`53002` 字典项不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/invoices/{invoiceId}")
    public Result<InvoiceVO> update(@PathVariable String invoiceId, Authentication authentication,
                                    @Valid @RequestBody UpdateInvoiceRequest request) {
        return Result.ok(invoiceService.update(invoiceId, authentication.getName(), request));
    }

    /**
     * 替换指定发票的主文件。
     *
     * <p>换掉发票原文件并记录一条文件修订，保留换票前后的追溯关系。新文件同样必须是本人上传、已就绪且未被引用的 `INVOICE_ORIGINAL`。
     *
     * <ul>
     *   <li>权限：申报人本人，发票处于 `DRAFT` 或 `RETURNED`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version`、`fileId`、`reason` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50001` 发票状态不允许；`60006` 退回时未开放 `currentFile`；`80000` 新文件未就绪；`80008` 新文件已被引用；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/replace-file")
    public Result<InvoiceVO> replaceFile(@PathVariable String invoiceId, Authentication authentication,
                                         @Valid @RequestBody ReplaceFileRequest request) {
        return Result.ok(invoiceService.replaceFile(invoiceId, authentication.getName(), request));
    }

    /**
     * 为指定发票添加附件。
     *
     * <p>追加付款记录、订单明细一类的佐证材料。附件文件必须是本人上传且已通过安全检测。
     *
     * <ul>
     *   <li>权限：申报人本人，发票处于 `DRAFT` 或 `RETURNED`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version`、`attachmentType`（`PAYMENT_RECORD`/`ORDER_DETAIL`/`OTHER`）、`fileId` 必填；`description` 可选。</li>
     *   <li>成功：`201`。</li>
     *   <li>常见错误：`50001` 发票状态不允许；`60006` 退回时未开放 `attachments`；`80000` 文件未就绪；`80008` 文件已被引用；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/attachments")
    public ResponseEntity<Result<AttachmentVO>> addAttachment(
            @PathVariable String invoiceId, Authentication authentication,
            @Valid @RequestBody AddAttachmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.ok(
                invoiceService.addAttachment(invoiceId, authentication.getName(), request)));
    }

    /**
     * 作废指定发票附件。
     *
     * <p>把附件标记为作废而不是物理删除，原记录保留可追溯。已作废的附件不会进入导出批次。
     *
     * <ul>
     *   <li>权限：申报人本人，发票处于 `DRAFT` 或 `RETURNED`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 与 `reason` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10003` 附件不存在；`50001` 发票状态不允许；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/attachments/{attachmentId}/void")
    public Result<AttachmentVO> voidAttachment(
            @PathVariable String invoiceId, @PathVariable String attachmentId,
            Authentication authentication, @Valid @RequestBody VersionedReasonRequest request) {
        return Result.ok(invoiceService.voidAttachment(
                invoiceId, attachmentId, authentication.getName(), request));
    }

    /**
     * 删除指定的发票草稿。
     *
     * <p>物理删除尚未提交的发票草稿，连同其附件与文件修订一并清理。已提交过的发票只能作废，不能删除。
     *
     * <ul>
     *   <li>权限：申报人本人，发票处于 `DRAFT`，且所属申请可编辑。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求参数：查询参数 `version` 必填，取发票当前乐观锁版本。</li>
     *   <li>成功：`200`，`data` 为空。</li>
     *   <li>常见错误：`50001` 发票不在 `DRAFT`；`42001` 申请状态不允许；`10007` 版本冲突。</li>
     * </ul>
     */
    @DeleteMapping("/invoices/{invoiceId}")
    public Result<Void> deleteDraft(@PathVariable String invoiceId, Authentication authentication,
                                    @RequestParam @Min(0) long version) {
        invoiceService.deleteDraft(invoiceId, authentication.getName(), version);
        return Result.ok();
    }

    /**
     * 作废指定的正式发票。
     *
     * <p>把已提交的发票作废并记录原因、操作人和时间，同时刷新所属申请的派生状态。作废是终态。
     *
     * <ul>
     *   <li>权限：申报人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 与 `reason` 必填。</li>
     *   <li>成功：`200`，`data.status` 为 `VOIDED`。</li>
     *   <li>常见错误：`50001` 发票处于 `DRAFT`、`VOIDED`、`IN_EXPORT_BATCH` 或 `ARCHIVED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/void")
    public Result<InvoiceVO> voidFormal(@PathVariable String invoiceId, Authentication authentication,
                                        @Valid @RequestBody VersionedReasonRequest request) {
        return Result.ok(invoiceService.voidFormal(invoiceId, authentication.getName(), request));
    }
}
