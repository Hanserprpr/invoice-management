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
     */
    @GetMapping("/applications/{applicationId}/invoices")
    public Result<List<InvoiceVO>> list(@PathVariable String applicationId) {
        return Result.ok(invoiceService.listForApplication(applicationId));
    }

    /**
     * 在指定申报下创建发票。
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
     */
    @GetMapping("/invoices/{invoiceId}")
    public Result<InvoiceVO> detail(@PathVariable String invoiceId) {
        return Result.ok(invoiceService.detail(invoiceId));
    }

    /**
     * 更新指定发票的信息。
     */
    @PatchMapping("/invoices/{invoiceId}")
    public Result<InvoiceVO> update(@PathVariable String invoiceId, Authentication authentication,
                                    @Valid @RequestBody UpdateInvoiceRequest request) {
        return Result.ok(invoiceService.update(invoiceId, authentication.getName(), request));
    }

    /**
     * 替换指定发票的主文件。
     */
    @PostMapping("/invoices/{invoiceId}/replace-file")
    public Result<InvoiceVO> replaceFile(@PathVariable String invoiceId, Authentication authentication,
                                         @Valid @RequestBody ReplaceFileRequest request) {
        return Result.ok(invoiceService.replaceFile(invoiceId, authentication.getName(), request));
    }

    /**
     * 为指定发票添加附件。
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
     */
    @DeleteMapping("/invoices/{invoiceId}")
    public Result<Void> deleteDraft(@PathVariable String invoiceId, Authentication authentication,
                                    @RequestParam @Min(0) long version) {
        invoiceService.deleteDraft(invoiceId, authentication.getName(), version);
        return Result.ok();
    }

    /**
     * 作废指定的正式发票。
     */
    @PostMapping("/invoices/{invoiceId}/void")
    public Result<InvoiceVO> voidFormal(@PathVariable String invoiceId, Authentication authentication,
                                        @Valid @RequestBody VersionedReasonRequest request) {
        return Result.ok(invoiceService.voidFormal(invoiceId, authentication.getName(), request));
    }
}
