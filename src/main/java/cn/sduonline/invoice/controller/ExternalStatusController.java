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
     */
    @GetMapping
    public Result<List<ExternalStatusEventVO>> list(@PathVariable String batchId) {
        return Result.ok(service.list(batchId));
    }

    /**
     * 向指定导出批次追加外部状态事件。
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
     */
    @PostMapping("/{eventId}/corrections")
    public ResponseEntity<Result<ExternalStatusEventVO>> correct(
            @PathVariable String batchId, @PathVariable String eventId,
            Authentication authentication, @Valid @RequestBody CorrectExternalEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.correct(batchId, eventId, authentication.getName(), request)));
    }
}
