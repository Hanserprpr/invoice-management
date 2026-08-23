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
     *
     * <p>返回该发票纸票的当前状态与乐观锁 `version`。纸票记录在发票内部通过时自动创建，未启用纸票的项目会直接拒绝。
     *
     * <ul>
     *   <li>权限：与台账相同的发票数据范围（申报人本人，或有权限的审核／审计／项目成员）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`61000` 项目未启用纸票；`61001` 纸票记录不存在；`50000` 发票不存在或不可见。</li>
     * </ul>
     */
    @GetMapping("/invoices/{invoiceId}/paper")
    public Result<PaperItemVO> detail(@PathVariable String invoiceId) {
        return Result.ok(service.detail(invoiceId));
    }

    /**
     * 分页查询指定项目的纸质票据。
     *
     * <p>项目维度的纸票清单，用于社团集中收取和盘点。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`，`pageSize` 默认 `20`、上限 `100`；`status` 可选，取值 `PENDING_DELIVERY`、`MEMBER_DECLARED`、`CLUB_RECEIVED`、`RETURNED_TO_MEMBER`、`TRANSFERRED_EXTERNAL`、`ARCHIVED`、`EXCEPTION`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`40000` 项目不存在；`61000` 项目未启用纸票；`10000` `status` 取值非法。</li>
     * </ul>
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
     *
     * <p>社员声明纸票已交出。状态从 `PENDING_DELIVERY` 转为 `MEMBER_DECLARED`，在社团确认收取前可以自行撤销。
     *
     * <ul>
     *   <li>权限：仅该发票的申报人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`（纸票记录的乐观锁版本）。</li>
     *   <li>成功：`200`，纸票状态为 `MEMBER_DECLARED`。</li>
     *   <li>常见错误：`61002` 纸票不在 `PENDING_DELIVERY`；`61000` 项目未启用纸票；`20003` 不是申报人本人；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/paper/declare")
    public Result<PaperItemVO> declare(@PathVariable String invoiceId, Authentication authentication,
                                       @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.declare(invoiceId, authentication.getName(), request));
    }

    /**
     * 撤销指定发票的纸质票据申报。
     *
     * <p>社员撤回声明，状态从 `MEMBER_DECLARED` 退回 `PENDING_DELIVERY`。社团一旦确认收取就不能再撤销。
     *
     * <ul>
     *   <li>权限：仅该发票的申报人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，纸票状态为 `PENDING_DELIVERY`。</li>
     *   <li>常见错误：`61002` 纸票不在 `MEMBER_DECLARED`；`20003` 不是申报人本人；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/paper/revoke-declaration")
    public Result<PaperItemVO> revoke(@PathVariable String invoiceId, Authentication authentication,
                                      @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.revokeDeclaration(invoiceId, authentication.getName(), request));
    }

    /**
     * 确认收到指定发票的纸质票据。
     *
     * <p>社团侧手工确认收取，状态转为 `CLUB_RECEIVED`。批量收取建议改用扫码接口。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，纸票状态为 `CLUB_RECEIVED`。</li>
     *   <li>常见错误：`61002` 纸票不在 `PENDING_DELIVERY`、`MEMBER_DECLARED` 或 `RETURNED_TO_MEMBER`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/paper/receive")
    public Result<PaperItemVO> receive(@PathVariable String invoiceId, Authentication authentication,
                                       @Valid @RequestBody PaperVersionRequest request) {
        return Result.ok(service.receive(invoiceId, authentication.getName(), request));
    }

    /**
     * 变更指定纸质票据的流转状态。
     *
     * <p>项目管理员手工更正纸票状态：退回社员、标记异常、外部移交或归档。每次变更都必须写明原因，并同时追加纸票事件和审计日志。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`status`、`reason`、`version` 必填。</li>
     *   <li>允许的迁移：`RETURNED_TO_MEMBER` 来自 `CLUB_RECEIVED`/`EXCEPTION`；`TRANSFERRED_EXTERNAL` 来自 `CLUB_RECEIVED`；`ARCHIVED` 来自 `CLUB_RECEIVED`/`TRANSFERRED_EXTERNAL`；`EXCEPTION` 来自除 `ARCHIVED`、`TRANSFERRED_EXTERNAL` 外的任意状态；`PENDING_DELIVERY` 来自 `RETURNED_TO_MEMBER`/`EXCEPTION`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`61002` 该迁移不被允许；`61003` 缺少原因；`20003` 无项目管理权限；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/invoices/{invoiceId}/paper/state")
    public Result<PaperItemVO> changeState(@PathVariable String invoiceId,
                                           Authentication authentication,
                                           @Valid @RequestBody PaperStateRequest request) {
        return Result.ok(service.changeState(invoiceId, authentication.getName(), request));
    }

    /**
     * 在指定项目中扫码处理纸质票据。
     *
     * <p>连续扫码收取入口。扫码原文不会落库，只保存 SHA-256 与解析出的最小票据标识。识别失败或状态不符时不会抛错，而是在结果里给出原因。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`rawPayload` 必填，不超过 2000 字。</li>
     *   <li>成功：`200`，`data.result` 为 `UNSUPPORTED`（无法解析）、`NOT_FOUND`（本项目无此票）、`POSSIBLE_DUPLICATE`（多个候选）、`ALREADY_SCANNED`（已收取）、`DATA_MISMATCH`（状态不允许收取）或收取成功。</li>
     *   <li>常见错误：`40000` 项目不存在；`61000` 项目未启用纸票；`20003` 无该项目审核权限。</li>
     * </ul>
     */
    @PostMapping("/projects/{projectId}/paper/scans")
    public Result<PaperScanResultVO> scan(@PathVariable String projectId,
                                          Authentication authentication,
                                          @Valid @RequestBody PaperScanRequest request) {
        return Result.ok(service.scan(projectId, authentication.getName(), request));
    }
}
