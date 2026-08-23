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
     *
     * <p>跑一遍查重与规则校验并追加结果：完全相同的数电票号、代码加号码或原文件摘要判为阻断；销售方税号、日期、金额同时相同判为警告。开始审核时也会自动执行同样的检查。
     *
     * <ul>
     *   <li>权限：对该发票有数据权限的人（申报人本人，或社团审核／审计角色与对应项目的授权成员）。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：无。</li>
     *   <li>成功：`200`，`data` 为本次执行后该发票的全部预检结果。</li>
     *   <li>常见错误：`50000` 发票不存在；`20003` 无该发票数据权限。</li>
     *   <li>备注：结果只追加不覆盖；跨社团命中不会返回另一社团的发票 ID 或内容。</li>
     * </ul>
     */
    @PostMapping
    public Result<List<PrecheckResultVO>> run(@PathVariable String invoiceId,
                                              Authentication authentication) {
        return Result.ok(service.run(invoiceId, authentication.getName()));
    }

    /**
     * 获取指定发票的预检结果。
     *
     * <p>读取历史预检结果，包含检查类型、严重级别、命中原因与处理结论，用于审核页展示风险提示。
     *
     * <ul>
     *   <li>权限：对该发票有数据权限的人。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，从未执行过预检时为空数组。</li>
     *   <li>常见错误：`50000` 发票不存在；`20003` 无该发票数据权限。</li>
     * </ul>
     */
    @GetMapping
    public Result<List<PrecheckResultVO>> list(@PathVariable String invoiceId) {
        return Result.ok(service.list(invoiceId));
    }

    /**
     * 处理指定的发票预检问题。
     *
     * <p>对某条命中给出人工结论。存在未处理的阻断项时审核无法通过，必须先在这里处理掉。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`resolution`（`CONFIRMED`/`FALSE_POSITIVE`/`ACCEPTED_RISK`）与 `comment` 必填。</li>
     *   <li>约束：`EXACT_DUPLICATE` 命中不允许用 `ACCEPTED_RISK` 放行。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10003` 结果不存在；`10000` 对精确重复使用了 `ACCEPTED_RISK`；`10001` 该命中已被处理；`20003` 无审核权限。</li>
     * </ul>
     */
    @PostMapping("/{resultId}/resolve")
    public Result<PrecheckResultVO> resolve(@PathVariable String invoiceId,
                                            @PathVariable String resultId,
                                            Authentication authentication,
                                            @Valid @RequestBody ResolvePrecheckRequest request) {
        return Result.ok(service.resolve(invoiceId, resultId, authentication.getName(), request));
    }
}
