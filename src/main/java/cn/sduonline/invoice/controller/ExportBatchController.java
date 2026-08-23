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

    /**
     * 按条件分页查询导出批次。
     *
     * <p>列出可见的导出批次。持有社团级 `application:review` 的人可见全社团，其他人只能看到自己有审核权限的项目。
     *
     * <ul>
     *   <li>权限：社团 `REVIEWER`／`CLUB_ADMIN`，或项目级 `REVIEW`/`MANAGE`；带 `projectId` 时先校验对该项目的权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`，`pageSize` 默认 `20`、上限 `100`；`status` 可选，取值 `DRAFT`、`GENERATED`、`EXPORTED`、`EXTERNAL_PROCESSING`、`COMPLETED`、`CANCELLED`、`ARCHIVED`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10000` `status` 取值非法；`20003` 对指定项目无权限。</li>
     * </ul>
     */
    @GetMapping
    public Result<PageResult<ExportBatchVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(page, pageSize, projectId, status));
    }

    /**
     * 创建导出批次。
     *
     * <p>把一组内部通过的发票组成 `DRAFT` 批次并写入占用记录。同一张发票不能同时进入两个未结束的批次，批次内发票必须同属一个项目。
     *
     * <ul>
     *   <li>权限：对这些发票所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`batchNo`（不超过 50 字，只允许字母、数字、`-`、`_`）与 `invoiceIds`（1–1000 条，不可重复）必填。</li>
     *   <li>成功：`201`，`data.status` 为 `DRAFT`，`revisionNo` 为 `1`，并返回票面与申请金额合计。</li>
     *   <li>常见错误：`70002` 存在非内部通过的发票；`70003` 发票跨项目；`70004` 发票已被其他未结束批次占用；`10005` 批次号重复。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<ExportBatchVO>> create(Authentication authentication,
                                                        @Valid @RequestBody CreateExportBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }

    /**
     * 获取指定导出批次的详情。
     *
     * <p>返回批次状态、发票快照清单和已生成的产物列表（含每个产物的 SHA-256）。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`70000` 批次不存在；`20003` 无该项目审核权限。</li>
     * </ul>
     */
    @GetMapping("/{batchId}")
    public Result<ExportBatchVO> detail(@PathVariable String batchId) {
        return Result.ok(service.detail(batchId));
    }

    /**
     * 提交指定批次的导出文件生成任务。
     *
     * <p>创建异步生成任务，工作线程从 R2 读取原票和有效附件，产出台账 XLSX、清单 PDF、附件 ZIP 与 `manifest.json`。已有进行中的任务时不会重复排队。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`（批次当前乐观锁版本）。</li>
     *   <li>成功：`202`，`data` 为当前批次；生成完成后状态才会变为 `GENERATED`，需自行轮询详情。</li>
     *   <li>常见错误：`70001` 批次不在 `DRAFT`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{batchId}/generate")
    public ResponseEntity<Result<ExportBatchVO>> generate(@PathVariable String batchId,
                                                          Authentication authentication,
                                                          @Valid @RequestBody BatchVersionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.ok(service.generate(batchId, authentication.getName(), request)));
    }

    /**
     * 为指定导出批次创建修订版本。
     *
     * <p>在 `GENERATED` 或 `EXPORTED` 的批次上新建一版：旧版本被归档，发票占用原子转移到新版本，`revisionNo` 递增。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`201`，`data` 为新修订，状态回到 `DRAFT`。</li>
     *   <li>常见错误：`70001` 批次不在 `GENERATED` 或 `EXPORTED`；`70000` 批次不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{batchId}/revisions")
    public ResponseEntity<Result<ExportBatchVO>> revise(@PathVariable String batchId,
                                                        Authentication authentication,
                                                        @Valid @RequestBody BatchVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.revise(batchId, authentication.getName(), request)));
    }

    /**
     * 取消指定导出批次。
     *
     * <p>取消 `DRAFT`、`GENERATED` 或 `EXPORTED` 的批次并释放其发票占用，这些发票随后可以进入其他批次。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `CANCELLED`。</li>
     *   <li>常见错误：`70001` 当前状态不允许取消；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{batchId}/cancel")
    public Result<ExportBatchVO> cancel(@PathVariable String batchId, Authentication authentication,
                                        @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.cancel(batchId, authentication.getName(), request));
    }

    /**
     * 将指定导出批次标记为已完成。
     *
     * <p>线下报销流程走完后置为 `COMPLETED`。与取消不同，完成会保留发票占用以维持可追溯性。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `COMPLETED`。</li>
     *   <li>常见错误：`70001` 批次不在 `EXPORTED` 或 `EXTERNAL_PROCESSING`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{batchId}/complete")
    public Result<ExportBatchVO> complete(@PathVariable String batchId, Authentication authentication,
                                          @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.complete(batchId, authentication.getName(), request));
    }

    /**
     * 归档指定导出批次。
     *
     * <p>把 `COMPLETED` 的批次置为 `ARCHIVED`，作为长期留存的终态。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `ARCHIVED`。</li>
     *   <li>常见错误：`70001` 批次不在 `COMPLETED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{batchId}/archive")
    public Result<ExportBatchVO> archive(@PathVariable String batchId, Authentication authentication,
                                         @Valid @RequestBody BatchVersionRequest request) {
        return Result.ok(service.archive(batchId, authentication.getName(), request));
    }

    /**
     * 获取指定导出产物的下载地址。
     *
     * <p>为某个已生成的产物签发短时预签名 `GET` 地址，同时记录下载时间和审计日志。
     *
     * <ul>
     *   <li>权限：对该批次所属项目有审核权限的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 含 `url` 与 `expiresAt`。</li>
     *   <li>常见错误：`70006` 产物不存在或尚未生成完成；`70000` 批次不存在；`80009` 对象存储未配置。</li>
     * </ul>
     */
    @GetMapping("/{batchId}/artifacts/{artifactId}/download-url")
    public Result<FileDownloadVO> download(@PathVariable String batchId, @PathVariable String artifactId,
                                           Authentication authentication) {
        return Result.ok(service.download(batchId, artifactId, authentication.getName()));
    }
}
