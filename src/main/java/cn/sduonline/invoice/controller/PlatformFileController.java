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
     *
     * <p>平台侧把人工或外部检测结论回写到文件上。只有回写为 `READY` 的文件才能被业务引用和下载——系统绝不自动判定文件安全。
     *
     * <ul>
     *   <li>权限：平台管理员。</li>
     *   <li>请求头：不需要 `X-Organization-Id`（`/api/platform/**` 不走租户过滤器），社团由路径 `organizationId` 指定；需 `X-XSRF-TOKEN`。</li>
     *   <li>请求体：`status` 必填，取值 `READY`、`REJECTED`、`FAILED`；`previewFileId` 可选，且该预览文件必须已是 `READY`。</li>
     *   <li>成功：`200`；文件已是 `READY` 时幂等返回当前状态。</li>
     *   <li>常见错误：`20003` 非平台管理员；`80007` 文件不存在；`10001` 文件不在 `PENDING`/`SCANNING`；`80000` 预览文件尚未就绪。</li>
     * </ul>
     */
    @PostMapping("/{fileId}/inspection")
    public Result<FileObjectVO> inspect(Authentication authentication,
                                        @PathVariable String organizationId,
                                        @PathVariable String fileId,
                                        @Valid @RequestBody InspectFileRequest request) {
        return Result.ok(fileService.inspect(authentication.getName(), organizationId, fileId, request));
    }
}
