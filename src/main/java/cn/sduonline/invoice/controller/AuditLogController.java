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
