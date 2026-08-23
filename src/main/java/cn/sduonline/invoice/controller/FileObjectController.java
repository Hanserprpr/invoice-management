package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.dto.FileDtos.CompleteUploadRequest;
import cn.sduonline.invoice.data.vo.FileDownloadVO;
import cn.sduonline.invoice.data.vo.FileObjectVO;
import cn.sduonline.invoice.data.vo.FileUploadVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.FileObjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class FileObjectController {
    private final FileObjectService fileService;

    public FileObjectController(FileObjectService fileService) {
        this.fileService = fileService;
    }

    /**
     * 登记已上传的文件对象。
     *
     * <p>只登记文件元数据并占位，不产生上传地址。浏览器直传场景请直接用 `POST /api/files/uploads`，它会在登记的同时返回预签名地址。
     *
     * <ul>
     *   <li>权限：任意社团成员，登记结果归属调用者本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`originalName`、`contentType`、`sizeBytes`（1–52428800）、`sha256`（64 位十六进制）、`purpose`（`INVOICE_ORIGINAL`/`PAYMENT_RECORD`/`ORDER_DETAIL`/`FORM_ATTACHMENT`/`OTHER`）必填。</li>
     *   <li>类型约束：只接受 PDF、OFD、JPEG、PNG，且扩展名必须与 `contentType` 匹配。</li>
     *   <li>成功：`201`，`data.scanStatus` 为 `PENDING`，记录 24 小时后过期。</li>
     *   <li>常见错误：`80001` 文件类型不被允许或与扩展名不符；`10000` 名称非法。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<FileObjectVO>> register(Authentication authentication,
                                                          @Valid @RequestBody RegisterFileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(fileService.register(authentication.getName(), request)));
    }

    /**
     * 获取指定文件对象的详情。
     *
     * <p>查询自己上传的文件当前的检测状态与元数据，用于轮询安全检测是否已完成。
     *
     * <ul>
     *   <li>权限：仅文件上传者本人；他人文件一律返回 `80007`。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data.scanStatus` 为 `PENDING`、`SCANNING`、`READY`、`REJECTED` 或 `FAILED`。</li>
     *   <li>常见错误：`80007` 文件不存在或不属于本人。</li>
     * </ul>
     */
    @GetMapping("/{fileId}")
    public Result<FileObjectVO> detail(@PathVariable String fileId) {
        return Result.ok(fileService.detail(fileId));
    }

    /**
     * 创建文件直传凭证并登记待上传文件。
     *
     * <p>浏览器直传的入口：登记元数据并返回一次性预签名 `PUT` 地址。文件二进制直接传到 R2 私有桶，应用不代理大文件流量。
     *
     * <ul>
     *   <li>权限：任意社团成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：与 `POST /api/files` 相同。</li>
     *   <li>成功：`201`，`data` 含 `file`、`method`（`PUT`）、`url`、`requiredHeaders` 与 `expiresAt`；`requiredHeaders` 必须原样带上，否则上传会被 R2 拒绝。</li>
     *   <li>常见错误：`80009` 对象存储尚未配置；`80001` 文件类型不被允许；`10001` 该文件已不在 `PENDING`。</li>
     *   <li>备注：预签名地址只写临时对象键，默认 10 分钟内有效（`R2_UPLOAD_URL_TTL`）。</li>
     * </ul>
     */
    @PostMapping("/uploads")
    public ResponseEntity<Result<FileUploadVO>> createUpload(
            Authentication authentication,
            @Valid @RequestBody RegisterFileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(fileService.createUpload(authentication.getName(), request)));
    }

    /**
     * 确认指定文件已上传完成。
     *
     * <p>直传结束后必须调用：服务端用 R2 `HEAD` 重新核对对象大小、类型和 SHA-256，再以服务端凭据复制到正式对象键、删除临时对象，并创建异步安全检测任务。
     *
     * <ul>
     *   <li>权限：仅文件上传者本人。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`sha256` 必填，且必须与登记时一致。</li>
     *   <li>成功：`200`，`data.scanStatus` 变为 `SCANNING`；已处于 `SCANNING`/`READY` 时幂等返回当前状态。</li>
     *   <li>常见错误：`80010` 对象未上传或内容与登记信息不符；`80007` 文件不存在或不属于本人；`80009` 对象存储未配置。</li>
     *   <li>备注：只有平台检测把状态回写为 `READY` 后，文件才能被业务引用和下载。</li>
     * </ul>
     */
    @PostMapping("/{fileId}/upload-complete")
    public Result<FileObjectVO> completeUpload(Authentication authentication,
                                                @PathVariable String fileId,
                                                @Valid @RequestBody CompleteUploadRequest request) {
        return Result.ok(fileService.completeUpload(authentication.getName(), fileId, request));
    }

    /**
     * 获取指定文件的下载地址。
     *
     * <p>签发短时预签名 `GET` 地址。签发前会重新校验租户、文件状态以及调用者是上传者还是有权访问关联发票的人——拿到地址不等于拥有业务授权。
     *
     * <ul>
     *   <li>权限：文件上传者本人，或对关联发票有数据权限的成员（社团审核／审计角色，或对应项目的授权成员）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 含 `url` 与 `expiresAt`，默认 5 分钟有效（`R2_DOWNLOAD_URL_TTL`）。</li>
     *   <li>常见错误：`80007` 文件不存在或无权访问；`80000` 文件尚未通过安全检测；`80009` 对象存储未配置。</li>
     * </ul>
     */
    @GetMapping("/{fileId}/download-url")
    public Result<FileDownloadVO> downloadUrl(@PathVariable String fileId) {
        return Result.ok(fileService.createDownload(fileId));
    }
}
