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
     */
    @GetMapping("/reviews/invoices/{invoiceId}")
    public Result<ReviewInvoiceDetailVO> detail(@PathVariable String invoiceId) {
        return Result.ok(reviewService.detail(invoiceId));
    }

    /**
     * 获取指定发票的审核历史。
     */
    @GetMapping("/invoices/{invoiceId}/reviews")
    public Result<List<ClubReviewVO>> history(@PathVariable String invoiceId) {
        return Result.ok(reviewService.history(invoiceId));
    }

    /**
     * 开始审核指定发票。
     */
    @PostMapping("/reviews/invoices/{invoiceId}/start")
    public Result<ReviewInvoiceDetailVO> start(@PathVariable String invoiceId,
                                               Authentication authentication,
                                               @Valid @RequestBody StartReviewRequest request) {
        return Result.ok(reviewService.start(invoiceId, authentication.getName(), request));
    }

    /**
     * 通过指定发票的审核。
     */
    @PostMapping("/reviews/invoices/{invoiceId}/approve")
    public Result<ReviewInvoiceDetailVO> approve(@PathVariable String invoiceId,
                                                 Authentication authentication,
                                                 @Valid @RequestBody ApproveReviewRequest request) {
        return Result.ok(reviewService.approve(invoiceId, authentication.getName(), request));
    }

    /**
     * 退回指定发票以便申报人修正。
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
     */
    @PostMapping("/reviews/invoices/{invoiceId}/reject")
    public Result<ReviewInvoiceDetailVO> reject(@PathVariable String invoiceId,
                                                Authentication authentication,
                                                @Valid @RequestBody RejectReviewRequest request) {
        return Result.ok(reviewService.reject(invoiceId, authentication.getName(), request));
    }

    /**
     * 批量通过发票审核。
     */
    @PostMapping("/reviews/invoices/batch/approve")
    public Result<List<ReviewInvoiceDetailVO>> batchApprove(
            Authentication authentication, @Valid @RequestBody BatchApproveRequest request) {
        return Result.ok(reviewService.batchApprove(authentication.getName(), request));
    }
}
