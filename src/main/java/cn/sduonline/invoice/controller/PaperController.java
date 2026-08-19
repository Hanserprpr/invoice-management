package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.PaperDtos.*;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.PaperItemVO;
import cn.sduonline.invoice.data.vo.PaperScanResultVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.PaperService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api")
public class PaperController {
    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    /**
     * 获取指定发票的纸质票据信息。
     */
    @GetMapping("/invoices/{invoiceId}/paper")
    public Result<PaperItemVO> detail(@PathVariable String invoiceId) {
        return Result.ok(service.detail(invoiceId));
    }

    /**
     * 分页查询指定项目的纸质票据。
     */
    @GetMapping("/projects/{projectId}/paper-items")
    public Result<PageResult<PaperItemVO>> list(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(page, pageSize, projectId, status));
    }

    /**
     * 申报指定发票存在纸质票据。
     */
    @PostMapping("/invoices/{invoiceId}/paper/declare")
    public Result<PaperItemVO> declare(@PathVariable String invoiceId, Authentication authentication,
                                       @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.declare(invoiceId, authentication.getName(), request));
    }

    /**
     * 撤销指定发票的纸质票据申报。
     */
    @PostMapping("/invoices/{invoiceId}/paper/revoke-declaration")
    public Result<PaperItemVO> revoke(@PathVariable String invoiceId, Authentication authentication,
                                      @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.revokeDeclaration(invoiceId, authentication.getName(), request));
    }

    /**
     * 确认收到指定发票的纸质票据。
     */
    @PostMapping("/invoices/{invoiceId}/paper/receive")
    public Result<PaperItemVO> receive(@PathVariable String invoiceId, Authentication authentication,
                                       @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.receive(invoiceId, authentication.getName(), request));
    }

    /**
     * 变更指定纸质票据的流转状态。
     */
    @PostMapping("/invoices/{invoiceId}/paper/state")
    public Result<PaperItemVO> changeState(@PathVariable String invoiceId,
                                           Authentication authentication,
                                           @Valid @RequestBody PaperStateRequest request) {
        return Result.ok(service.changeState(invoiceId, authentication.getName(), request));
    }

    /**
     * 在指定项目中扫码处理纸质票据。
     */
    @PostMapping("/projects/{projectId}/paper/scans")
    public Result<PaperScanResultVO> scan(@PathVariable String projectId,
                                          Authentication authentication,
                                          @Valid @RequestBody PaperScanRequest request) {
        return Result.ok(service.scan(projectId, authentication.getName(), request));
    }
}
