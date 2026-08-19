package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.FileDtos.InspectFileRequest;
import cn.sduonline.invoice.data.vo.FileObjectVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.FileObjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/organizations/{organizationId}/files")
public class PlatformFileController {
    private final FileObjectService fileService;

    public PlatformFileController(FileObjectService fileService) {
        this.fileService = fileService;
    }

    /**
     * 对指定组织的文件执行平台级安全复检。
     */
    @PostMapping("/{fileId}/inspection")
    public Result<FileObjectVO> inspect(Authentication authentication,
                                        @PathVariable String organizationId,
                                        @PathVariable String fileId,
                                        @Valid @RequestBody InspectFileRequest request) {
        return Result.ok(fileService.inspect(authentication.getName(), organizationId, fileId, request));
    }
}
