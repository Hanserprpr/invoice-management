package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.ExternalStatusDtos.*;
import cn.sduonline.invoice.data.vo.ExternalStatusEventVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.ExternalStatusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/export-batches/{batchId}/external-events")
public class ExternalStatusController {
    private final ExternalStatusService service;
    public ExternalStatusController(ExternalStatusService service) { this.service = service; }

    /**
     * 获取指定导出批次的外部状态事件。
     *
     * <p>按时间列出该批次在平台之外的流转记录（送审、退回、完成等）及其更正事件，形成只追加的事实链。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`70000` 批次不存在；`20003` 无该项目审核权限。</li>
     * </ul>
     */
    @GetMapping
    public Result<List<ExternalStatusEventVO>> list(@PathVariable String batchId) {
        return Result.ok(service.list(batchId));
    }

    /**
     * 向指定导出批次追加外部状态事件。
     *
     * <p>记录一次平台外进展。事件只追加不修改，写错了要用更正接口而不是改原记录；追加 `RETURNED_EXTERNAL` 会同时通知相关成员。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`eventType`（`STATUS_CHANGE`/`COMMENT`）、`status`（`PENDING_EXTERNAL`/`SUBMITTED_EXTERNAL`/`RETURNED_EXTERNAL`/`COMPLETED`/`CANCELLED`/`ARCHIVED`）、`batchVersion` 必填；`comment` 在 `COMMENT` 时必填；`attachmentFileIds` 至多 20 个，需为本人上传且已就绪的 `OTHER`/`FORM_ATTACHMENT` 文件。</li>
     *   <li>成功：`201`。</li>
     *   <li>常见错误：`70001` 批次不在 `GENERATED`/`EXPORTED`/`EXTERNAL_PROCESSING`/`COMPLETED`；`10000` `COMMENT` 缺少内容或附件重复；`80000` 附件未就绪；`10007` 批次版本冲突。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<ExternalStatusEventVO>> append(
            @PathVariable String batchId, Authentication authentication,
            @Valid @RequestBody CreateExternalEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.append(batchId, authentication.getName(), request)));
    }

    /**
     * 对指定外部状态事件进行更正。
     *
     * <p>追加一条指向被更正事件的 `CORRECTION` 事件，原事件保持原样。更正事件本身不能再被更正。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`status`、`reason`、`batchVersion` 必填；`attachmentFileIds` 可选，至多 20 个。</li>
     *   <li>成功：`201`。</li>
     *   <li>常见错误：`71000` 被更正事件不存在；`71001` 被更正的本身就是更正事件；`70001` 批次状态不允许；`10007` 批次版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{eventId}/corrections")
    public ResponseEntity<Result<ExternalStatusEventVO>> correct(
            @PathVariable String batchId, @PathVariable String eventId,
            Authentication authentication, @Valid @RequestBody CorrectExternalEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.correct(batchId, eventId, authentication.getName(), request)));
    }
}
