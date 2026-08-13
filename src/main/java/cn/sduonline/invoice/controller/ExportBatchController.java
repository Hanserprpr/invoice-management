package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.ExportDtos.*;
import cn.sduonline.invoice.data.vo.*;
import cn.sduonline.invoice.service.ExportBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/export-batches")
public class ExportBatchController {
    private final ExportBatchService service;

    public ExportBatchController(ExportBatchService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<ExportBatchVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(page, pageSize, projectId, status));
    }

    @PostMapping
    public ResponseEntity<Result<ExportBatchVO>> create(Authentication authentication,
                                                        @Valid @RequestBody CreateExportBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }

    @GetMapping("/{batchId}")
    public Result<ExportBatchVO> detail(@PathVariable String batchId) {
        return Result.ok(service.detail(batchId));
    }

    @PostMapping("/{batchId}/generate")
    public ResponseEntity<Result<ExportBatchVO>> generate(@PathVariable String batchId,
                                                          Authentication authentication,
                                                          @Valid @RequestBody BatchVersionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.ok(service.generate(batchId, authentication.getName(), request)));
    }

    @PostMapping("/{batchId}/revisions")
    public ResponseEntity<Result<ExportBatchVO>> revise(@PathVariable String batchId,
                                                        Authentication authentication,
                                                        @Valid @RequestBody BatchVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.revise(batchId, authentication.getName(), request)));
    }

    @PostMapping("/{batchId}/cancel")
    public Result<ExportBatchVO> cancel(@PathVariable String batchId, Authentication authentication,
                                        @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.cancel(batchId, authentication.getName(), request));
    }

    @PostMapping("/{batchId}/complete")
    public Result<ExportBatchVO> complete(@PathVariable String batchId, Authentication authentication,
                                          @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.complete(batchId, authentication.getName(), request));
    }

    @PostMapping("/{batchId}/archive")
    public Result<ExportBatchVO> archive(@PathVariable String batchId, Authentication authentication,
                                         @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.archive(batchId, authentication.getName(), request));
    }

    @GetMapping("/{batchId}/artifacts/{artifactId}/download-url")
    public Result<FileDownloadVO> download(@PathVariable String batchId, @PathVariable String artifactId,
                                           Authentication authentication) {
        return Result.ok(service.download(batchId, artifactId, authentication.getName()));
    }
}
