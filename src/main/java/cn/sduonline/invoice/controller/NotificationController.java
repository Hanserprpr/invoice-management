package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    /**
     * 分页查询当前用户的通知收件箱。
     */
    @GetMapping
    public Result<PageResult<NotificationVO>> inbox(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(service.inbox(page, pageSize, status));
    }

    /**
     * 将指定通知标记为已读。
     */
    @PostMapping("/{id}/read")
    public Result<NotificationVO> read(@PathVariable String id) { return Result.ok(service.read(id)); }

    /**
     * 忽略指定通知。
     */
    @PostMapping("/{id}/dismiss")
    public Result<NotificationVO> dismiss(@PathVariable String id) { return Result.ok(service.dismiss(id)); }
}
