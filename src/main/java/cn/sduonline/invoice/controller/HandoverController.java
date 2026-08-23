package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.HandoverDtos.CreateHandoverRequest;
import cn.sduonline.invoice.data.vo.HandoverRecordVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.HandoverService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/handovers")
public class HandoverController {
    private final HandoverService service;
    public HandoverController(HandoverService service) { this.service = service; }

    /**
     * 获取纸质票据交接记录。
     *
     * <p>列出社团历次换届交接及其快照（移交的项目、授权数量、执行人），用于追溯职责变更。
     *
     * <ul>
     *   <li>权限：`member:manage`（默认属于 `CLUB_ADMIN`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`20003` 缺少 `member:manage`。</li>
     * </ul>
     */
    @GetMapping
    public Result<List<HandoverRecordVO>> list() { return Result.ok(service.list()); }

    /**
     * 创建纸质票据交接记录。
     *
     * <p>执行一次换届交接：把离任成员的项目负责人身份与项目授权整体转移给接任成员，结束离任成员的成员关系，并给接任者发一条站内通知。整个过程在一个事务里完成。
     *
     * <ul>
     *   <li>权限：`member:manage`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`outgoingCasId`、`incomingCasId`、`outgoingVersion` 必填且两人不能相同；`comment` 可选。</li>
     *   <li>前置条件：两人都必须是该社团 `ACTIVE` 状态的成员。</li>
     *   <li>成功：`201`，`data` 含移交的项目 ID 列表与授权条数。</li>
     *   <li>常见错误：`32000` 成员关系不存在；`10001` 某一方不是 `ACTIVE`；`10000` 两个学号相同；`10007` 离任成员版本冲突。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<HandoverRecordVO>> create(
            Authentication authentication, @Valid @RequestBody CreateHandoverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }
}
