package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.PrecheckDtos.ResolvePrecheckRequest;
import cn.sduonline.invoice.data.vo.PrecheckResultVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.InvoicePrecheckService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/prechecks")
public class PrecheckController {
    private final InvoicePrecheckService service;

    public PrecheckController(InvoicePrecheckService service) {
        this.service = service;
    }

    /**
     * 对指定发票执行提交前检查。
     */
    @PostMapping
    public Result<List<PrecheckResultVO>> run(@PathVariable String invoiceId,
                                              Authentication authentication) {
        return Result.ok(service.run(invoiceId, authentication.getName()));
    }

    /**
     * 获取指定发票的预检结果。
     */
    @GetMapping
    public Result<List<PrecheckResultVO>> list(@PathVariable String invoiceId) {
        return Result.ok(service.list(invoiceId));
    }

    /**
     * 处理指定的发票预检问题。
     */
    @PostMapping("/{resultId}/resolve")
    public Result<PrecheckResultVO> resolve(@PathVariable String invoiceId,
                                            @PathVariable String resultId,
                                            Authentication authentication,
                                            @Valid @RequestBody ResolvePrecheckRequest request) {
        return Result.ok(service.resolve(invoiceId, resultId, authentication.getName(), request));
    }
}
