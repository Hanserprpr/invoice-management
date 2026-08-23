package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.UpdateOrganizationRequest;
import cn.sduonline.invoice.data.vo.OrganizationVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.OrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/platform/organizations")
public class PlatformOrganizationController {
    private final OrganizationService organizationService;

    public PlatformOrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public Result<PageResult<OrganizationVO>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(organizationService.listPlatform(authentication.getName(), page, pageSize,
                keyword, status));
    }

    @GetMapping("/{organizationId}")
    public Result<OrganizationVO> detail(Authentication authentication,
                                         @PathVariable String organizationId) {
        return Result.ok(organizationService.getPlatform(authentication.getName(), organizationId));
    }

    /**
     * 创建组织。
     *
     * <p>平台管理员开通一个新社团：创建或复用初始管理员账号、授予 `CLUB_ADMIN` 角色，并把平台字典模板克隆到该社团。
     *
     * <ul>
     *   <li>权限：平台管理员（`user.is_platform_admin`）。</li>
     *   <li>请求头：不需要 `X-Organization-Id`（`/api/platform/**` 不走租户过滤器）；需 `X-XSRF-TOKEN`。</li>
     *   <li>请求体：`name` 必填且全局唯一；`type` 可选，缺省为 `CLUB`；`initialAdmin` 必填，含 `casId`、`name` 与可选任期。</li>
     *   <li>成功：`201`，`data` 为新建社团。</li>
     *   <li>常见错误：`20003` 非平台管理员；`31002` 社团重名；`30001` 初始管理员账号已被禁用；`32004` 任期范围非法。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<OrganizationVO>> create(Authentication authentication,
                                                         @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(organizationService.create(authentication.getName(), request)));
    }

    /**
     * 更新指定组织的信息。
     *
     * <p>修改社团名称，或在 `ACTIVE` 与 `DISABLED` 之间切换状态；停用后该社团禁止新增项目和申请。
     *
     * <ul>
     *   <li>权限：平台管理员。</li>
     *   <li>请求头：不需要 `X-Organization-Id`；需 `X-XSRF-TOKEN`。</li>
     *   <li>请求体：`version` 必填（乐观锁）；`name`、`status` 可选，只提交需要修改的字段。</li>
     *   <li>成功：`200`，`data.version` 已自增。</li>
     *   <li>常见错误：`20003` 非平台管理员；`31000` 社团不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/{organizationId}")
    public Result<OrganizationVO> update(Authentication authentication,
                                         @PathVariable String organizationId,
                                         @Valid @RequestBody UpdateOrganizationRequest request) {
        return Result.ok(organizationService.update(authentication.getName(), organizationId, request));
    }
}
