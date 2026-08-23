package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.AuditLogVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.AuditQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditQueryService service;
    public AuditLogController(AuditQueryService service) { this.service = service; }

    /**
     * 按条件分页查询审计日志。
     *
     * <p>只读的审计流水查询，覆盖社团内的关键写操作。日志只追加，没有任何修改或删除接口。
     *
     * <ul>
     *   <li>权限：`audit-log:read`（默认属于 `AUDITOR` 和 `CLUB_ADMIN`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`，`pageSize` 默认 `20`、上限 `100`；`actorCasId`、`action`、`objectType`、`objectId` 均可选，用于按操作人、动作或对象过滤。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`20003` 缺少 `audit-log:read`；`10000` 分页参数超出范围。</li>
     * </ul>
     */
    @GetMapping
    public Result<PageResult<AuditLogVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String actorCasId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId) {
        return Result.ok(service.list(page, pageSize, actorCasId, action, objectType, objectId));
    }
}
