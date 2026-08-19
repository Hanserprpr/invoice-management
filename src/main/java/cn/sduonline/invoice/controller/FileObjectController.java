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
     */
    @PostMapping
    public ResponseEntity<Result<FileObjectVO>> register(Authentication authentication,
                                                          @Valid @RequestBody RegisterFileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(fileService.register(authentication.getName(), request)));
    }

    /**
     * 获取指定文件对象的详情。
     */
    @GetMapping("/{fileId}")
    public Result<FileObjectVO> detail(@PathVariable String fileId) {
        return Result.ok(fileService.detail(fileId));
    }

    /**
     * 创建文件直传凭证并登记待上传文件。
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
     */
    @PostMapping("/{fileId}/upload-complete")
    public Result<FileObjectVO> completeUpload(Authentication authentication,
                                                @PathVariable String fileId,
                                                @Valid @RequestBody CompleteUploadRequest request) {
        return Result.ok(fileService.completeUpload(authentication.getName(), fileId, request));
    }

    /**
     * 获取指定文件的下载地址。
     */
    @GetMapping("/{fileId}/download-url")
    public Result<FileDownloadVO> downloadUrl(@PathVariable String fileId) {
        return Result.ok(fileService.createDownload(fileId));
    }
}
