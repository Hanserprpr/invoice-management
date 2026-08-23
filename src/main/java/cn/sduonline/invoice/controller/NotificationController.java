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
     *
     * <p>返回本人在当前社团的站内通知。通知先入库再异步投递外部渠道，正文不含文件内容、令牌或跨租户数据。
     *
     * <ul>
     *   <li>权限：仅本人，其他人的通知不可见。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`，`pageSize` 默认 `20`、上限 `100`；`status` 可选，取值 `UNREAD`、`READ`、`DISMISSED`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10000` `status` 取值非法。</li>
     * </ul>
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
     *
     * <p>把本人的一条通知置为 `READ` 并记录已读时间。
     *
     * <ul>
     *   <li>权限：仅通知的接收人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：无。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`72000` 通知不存在或不属于本人。</li>
     * </ul>
     */
    @PostMapping("/{id}/read")
    public Result<NotificationVO> read(@PathVariable String id) { return Result.ok(service.read(id)); }

    /**
     * 忽略指定通知。
     *
     * <p>把本人的一条通知置为 `DISMISSED`，使其不再出现在未读列表中。通知记录本身保留。
     *
     * <ul>
     *   <li>权限：仅通知的接收人本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：无。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`72000` 通知不存在或不属于本人。</li>
     * </ul>
     */
    @PostMapping("/{id}/dismiss")
    public Result<NotificationVO> dismiss(@PathVariable String id) { return Result.ok(service.dismiss(id)); }
}
