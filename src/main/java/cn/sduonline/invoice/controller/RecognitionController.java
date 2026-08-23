package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.RecognitionDtos.ConfirmSuggestionsRequest;
import cn.sduonline.invoice.data.vo.RecognitionJobVO;
import cn.sduonline.invoice.data.vo.RecognitionSuggestionVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.RecognitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/recognition")
public class RecognitionController {
    private final RecognitionService recognitionService;

    public RecognitionController(RecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    /**
     * 启动指定发票的智能识别任务。
     *
     * <p>创建异步 OCR／二维码识别任务。同一张发票已有等待中或执行中的任务时直接返回原任务，不会重复排队。
     *
     * <ul>
     *   <li>权限：发票所属申请的本人，或对该发票有数据权限的审核／审计角色。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：无。</li>
     *   <li>成功：`202`，`data` 为识别任务，`status` 为 `PENDING`。</li>
     *   <li>常见错误：`50000` 发票不存在或无权访问；`50001` 发票已作废或已归档。</li>
     *   <li>备注：未配置 OCR 时任务会成功落到 `MANUAL_ENTRY`，不阻断人工录入。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<RecognitionJobVO>> start(
            @PathVariable String invoiceId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.ok(recognitionService.start(invoiceId, authentication.getName())));
    }

    /**
     * 获取指定发票的识别任务记录。
     *
     * <p>按创建时间倒序列出该发票的历史识别任务及其状态与重试次数，用于前端轮询识别进度。
     *
     * <ul>
     *   <li>权限：同发票数据权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50000` 发票不存在或无权访问。</li>
     * </ul>
     */
    @GetMapping("/jobs")
    public Result<List<RecognitionJobVO>> jobs(@PathVariable String invoiceId) {
        return Result.ok(recognitionService.jobs(invoiceId));
    }

    /**
     * 获取指定发票的识别建议。
     *
     * <p>返回结构化的字段级建议，每条含建议值与置信度。识别原文只留在任务结果里，不通过本接口返回。
     *
     * <ul>
     *   <li>权限：同发票数据权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 为建议数组。</li>
     *   <li>常见错误：`50000` 发票不存在或无权访问。</li>
     * </ul>
     */
    @GetMapping("/suggestions")
    public Result<List<RecognitionSuggestionVO>> suggestions(@PathVariable String invoiceId) {
        return Result.ok(recognitionService.suggestions(invoiceId));
    }

    /**
     * 确认或修正指定发票的识别建议。
     *
     * <p>逐条接受、更正或拒绝识别建议——识别结果永远不会自动写入发票，必须经过这一步人工确认。
     *
     * <ul>
     *   <li>权限：同发票数据权限，且发票处于 `DRAFT` 或 `RETURNED`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`decisions` 必填，1–30 条，每条含 `suggestionId`、`status`（`ACCEPTED`/`CORRECTED`/`REJECTED`），`CORRECTED` 时需给出 `finalValue`；`suggestionId` 不可重复。</li>
     *   <li>成功：`200`，`data` 为该发票最新的全部建议。</li>
     *   <li>常见错误：`50001` 发票状态不允许；`10003` 建议不存在；`10000` 决策重复或参数非法；`10001` 该建议已被确认过。</li>
     * </ul>
     */
    @PutMapping("/suggestions")
    public Result<List<RecognitionSuggestionVO>> confirm(
            @PathVariable String invoiceId, Authentication authentication,
            @Valid @RequestBody ConfirmSuggestionsRequest request) {
        return Result.ok(recognitionService.confirm(invoiceId, authentication.getName(), request));
    }
}
