package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.ReplaceRolesRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.UpdateMemberRequest;
import cn.sduonline.invoice.data.vo.MemberVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.MemberService;
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
@RequestMapping("/api/organizations/{organizationId}/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 分页查询指定组织的成员。
     *
     * <p>按学号升序列出社团成员及其状态、任期、角色与项目授权，用于成员管理页。
     *
     * <ul>
     *   <li>权限：`member:manage`（默认属于 `CLUB_ADMIN`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。且必须等于路径 `organizationId`。</li>
     *   <li>请求参数：`page` 默认 `1`；`pageSize` 默认 `20`，上限 `100`。</li>
     *   <li>成功：`200`，`data` 为分页结果（`records`、`total`）。</li>
     *   <li>常见错误：`20003` 缺少 `member:manage`；`20004` 路径与租户头不一致。</li>
     * </ul>
     */
    @GetMapping
    public Result<PageResult<MemberVO>> list(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(memberService.list(organizationId, page, pageSize));
    }

    /**
     * 向指定组织添加成员。
     *
     * <p>按学号登记社团成员并自动授予 `MEMBER` 角色；平台上尚不存在的用户会一并创建。更高的角色需再调用角色替换接口。
     *
     * <ul>
     *   <li>权限：`member:manage`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`casId`（`^[A-Za-z0-9_-]{1,20}$`）与 `name` 必填；`termStart`、`termEnd` 可选，填写时结束日期不得早于开始日期。</li>
     *   <li>成功：`201`。</li>
     *   <li>常见错误：`32003` 成员关系已存在；`30001` 该账号已被禁用；`32004` 任期范围非法。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<MemberVO>> create(
            @PathVariable String organizationId,
            Authentication authentication,
            @Valid @RequestBody CreateMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(memberService.create(organizationId, authentication.getName(), request)));
    }

    /**
     * 更新指定组织成员的信息。
     *
     * <p>调整成员状态或任期。状态在 `ACTIVE`、`INACTIVE`、`LEFT` 之间取值，已离任（`LEFT`）的成员不能直接恢复为 `ACTIVE`。
     *
     * <ul>
     *   <li>权限：`member:manage`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`status`、`termStart`、`termEnd` 可选；清空任期请用 `clearTermStart`、`clearTermEnd`，仅传 `null` 视为不修改。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`32000` 成员关系不存在；`10001` `LEFT` 不可直接恢复；`32004` 任期范围非法；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/{casId}")
    public Result<MemberVO> update(@PathVariable String organizationId,
                                   @PathVariable String casId,
                                   Authentication authentication,
                                   @Valid @RequestBody UpdateMemberRequest request) {
        return Result.ok(memberService.update(organizationId, casId, authentication.getName(), request));
    }

    /**
     * 替换指定组织成员的角色集合。
     *
     * <p>整体覆盖该成员的角色与项目授权：请求里没出现的角色和授权会被删除，因此必须提交完整的目标集合而不是增量。
     *
     * <ul>
     *   <li>权限：`role:manage`（默认属于 `CLUB_ADMIN`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`roles` 取值限 `MEMBER`、`PROJECT_MANAGER`、`REVIEWER`、`CLUB_ADMIN`、`AUDITOR`，不可重复，`effectiveUntil` 不得早于 `effectiveFrom`；`projectGrants` 的 `accessTypes` 取值限 `VIEW`、`SUBMIT`、`REVIEW`、`MANAGE`。</li>
     *   <li>成功：`200`，成员 `version` 自增。</li>
     *   <li>常见错误：`32005` 角色编码非法或重复；`32006` 项目不属于本社团或授权重复；`32004` 生效区间非法；`10007` 版本冲突。</li>
     * </ul>
     */
    @PutMapping("/{casId}/roles")
    public Result<MemberVO> replaceRoles(@PathVariable String organizationId,
                                         @PathVariable String casId,
                                         Authentication authentication,
                                         @Valid @RequestBody ReplaceRolesRequest request) {
        return Result.ok(memberService.replaceRoles(organizationId, casId,
                authentication.getName(), request));
    }
}
