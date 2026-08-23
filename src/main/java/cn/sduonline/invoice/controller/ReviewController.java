package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.ReviewDtos.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 按条件分页查询待审核发票队列。
     *
     * <p>审核工作台的列表：持有社团级 `application:review` 的人可见全社团，其他人只能看到自己有 `REVIEW`/`MANAGE` 范围的项目。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或项目级 `REVIEW`/`MANAGE`；带 `projectId` 时会先校验对该项目的审核权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`；`pageSize` 默认 `20`，上限 `100`；`projectId`、`applicantCasId` 可选；`status` 可选，取值 `SUBMITTED`、`IN_REVIEW`、`RETURNED`、`INTERNALLY_APPROVED`、`REJECTED`、`VOIDED`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10000` `status` 取值非法；`20003` 对指定项目无审核权限。</li>
     * </ul>
     */
    @GetMapping("/reviews/invoices")
    public Result<PageResult<ReviewQueueItemVO>> queue(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicantCasId) {
        return Result.ok(reviewService.queue(page, pageSize, projectId, status, applicantCasId));
    }

    /**
     * 获取指定待审核发票的详情。
     *
     * <p>一次返回队列摘要、发票全字段、社团内部备注、标签与完整审核历史，是单票整理页的主数据源。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50000` 发票不存在；`20003` 无该项目审核权限。</li>
     * </ul>
     */
    @GetMapping("/reviews/invoices/{invoiceId}")
    public Result<ReviewInvoiceDetailVO> detail(@PathVariable String invoiceId) {
        return Result.ok(reviewService.detail(invoiceId));
    }

    /**
     * 获取指定发票的审核历史。
     *
     * <p>只读的审核动作流水，含开始审核、通过、退回、拒绝及其原因和批量操作标识。申报人本人也能查看自己发票的审核过程。
     *
     * <ul>
     *   <li>权限：发票申报人本人，或对该项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`50000` 发票不存在；`20003` 既不是本人也没有审核权限。</li>
     * </ul>
     */
    @GetMapping("/invoices/{invoiceId}/reviews")
    public Result<List<ClubReviewVO>> history(@PathVariable String invoiceId) {
        return Result.ok(reviewService.history(invoiceId));
    }

    /**
     * 开始审核指定发票。
     *
     * <p>把 `SUBMITTED` 的发票锁定为 `IN_REVIEW`，并自动执行一次查重与规则预检。所有单票结论都必须先经过这一步。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`（发票当前乐观锁版本）。</li>
     *   <li>成功：`200`；发票已是 `IN_REVIEW` 时幂等返回当前详情。</li>
     *   <li>常见错误：`10001` 发票不在 `SUBMITTED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/reviews/invoices/{invoiceId}/start")
    public Result<ReviewInvoiceDetailVO> start(@PathVariable String invoiceId,
                                               Authentication authentication,
                                               @Valid @RequestBody StartReviewRequest request) {
        return Result.ok(reviewService.start(invoiceId, authentication.getName(), request));
    }

    /**
     * 通过指定发票的审核。
     *
     * <p>作出内部通过结论，可同时补充费用类别、内部备注和标签。项目启用纸票时，通过后会幂等创建一条 `PENDING_DELIVERY` 纸票记录。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`expenseCategoryItemId`、`internalNote`、`tagItemIds` 可选，`tagItemIds` 一旦提交即为整体替换。</li>
     *   <li>前置条件：不能存在未处理的阻断级预检命中。</li>
     *   <li>成功：`200`，`data` 中发票状态为 `INTERNALLY_APPROVED`。</li>
     *   <li>常见错误：`60000` 尚未开始审核；`53001` 存在未处理的阻断项；`53002` 字典项不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/reviews/invoices/{invoiceId}/approve")
    public Result<ReviewInvoiceDetailVO> approve(@PathVariable String invoiceId,
                                                 Authentication authentication,
                                                 @Valid @RequestBody ApproveReviewRequest request) {
        return Result.ok(reviewService.approve(invoiceId, authentication.getName(), request));
    }

    /**
     * 退回指定发票以便申报人修正。
     *
     * <p>把发票退回给申报人并指定可修改的字段。申报人只能改这些字段，表单原始答案保持不变。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version`、`reasonItemId`（`RETURN_REASON` 字典项）、`returnFields`（1–20 项）必填；`comment` 可选。</li>
     *   <li>可选字段：`returnFields` 取值限发票字段白名单，另含 `currentFile`（允许换原文件）和 `attachments`（允许改附件）。</li>
     *   <li>成功：`200`，`data` 中发票状态为 `RETURNED`。</li>
     *   <li>常见错误：`60000` 尚未开始审核；`60003` 未指定可修改字段；`53002` 退回原因字典项不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/reviews/invoices/{invoiceId}/return")
    public Result<ReviewInvoiceDetailVO> returnForCorrection(
            @PathVariable String invoiceId, Authentication authentication,
            @Valid @RequestBody ReturnReviewRequest request) {
        return Result.ok(reviewService.returnForCorrection(
                invoiceId, authentication.getName(), request));
    }

    /**
     * 驳回指定发票的审核。
     *
     * <p>作出拒绝结论。与退回不同，拒绝不给申报人修改机会，必须引用已发布的原因字典项。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 与 `reasonItemId` 必填；`comment` 可选。</li>
     *   <li>成功：`200`，`data` 中发票状态为 `REJECTED`。</li>
     *   <li>常见错误：`60000` 尚未开始审核；`53002` 原因字典项不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/reviews/invoices/{invoiceId}/reject")
    public Result<ReviewInvoiceDetailVO> reject(@PathVariable String invoiceId,
                                                Authentication authentication,
                                                @Valid @RequestBody RejectReviewRequest request) {
        return Result.ok(reviewService.reject(invoiceId, authentication.getName(), request));
    }

    /**
     * 批量通过发票审核。
     *
     * <p>一次通过多张发票，共享同一个批量操作标识便于追溯。仍处于 `SUBMITTED` 的发票会先补记一条隐式的开始审核记录并跑预检。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或对该项目有 `REVIEW`/`MANAGE` 范围的成员。逐张校验，其中任意一张不满足条件都会让整批回滚。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`invoices` 必填，1–100 项，每项含 `invoiceId` 与该票的 `version`，`invoiceId` 不可重复。</li>
     *   <li>成功：`200`，`data` 为每张发票通过后的详情数组。</li>
     *   <li>常见错误：`60005` 超出 100 条上限；`53001` 某张票存在未处理的阻断项；`10001` 某张票状态不允许；`10007` 某张票版本冲突。</li>
     * </ul>
     */
    @PostMapping("/reviews/invoices/batch/approve")
    public Result<List<ReviewInvoiceDetailVO>> batchApprove(
            Authentication authentication, @Valid @RequestBody BatchApproveRequest request) {
        return Result.ok(reviewService.batchApprove(authentication.getName(), request));
    }
}
