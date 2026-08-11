package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.FileDtos.RegisterFileRequest;
import cn.sduonline.invoice.data.vo.FileObjectVO;
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

    @PostMapping
    public ResponseEntity<Result<FileObjectVO>> register(Authentication authentication,
                                                          @Valid @RequestBody RegisterFileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(fileService.register(authentication.getName(), request)));
    }

    @GetMapping("/{fileId}")
    public Result<FileObjectVO> detail(@PathVariable String fileId) {
        return Result.ok(fileService.detail(fileId));
    }
}
