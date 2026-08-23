package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.UpdateProjectRequest;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.ProjectVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 按条件分页查询项目。
     *
     * <p>只返回当前用户可见的项目：`CLUB_ADMIN` 可见全部，其他人可见 `visibility=ALL` 的项目以及本人担任负责人或获得授权的项目。
     *
     * <ul>
     *   <li>权限：社团成员，结果按可见范围过滤。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`；`pageSize` 默认 `20`，上限 `100`；`status` 可选，取值 `DRAFT`、`COLLECTING`、`COLLECTION_STOPPED`、`ORGANIZING`、`ARCHIVED`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10000` `status` 取值非法。</li>
     * </ul>
     */
    @GetMapping
    public Result<PageResult<ProjectVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(projectService.list(page, pageSize, status));
    }

    /**
     * 创建项目。
     *
     * <p>创建处于 `DRAFT` 状态的项目，同时写入负责人与项目级授权。项目要先 `open` 才能开始收集。
     *
     * <ul>
     *   <li>权限：`project:create`（默认属于 `CLUB_ADMIN` 和 `PROJECT_MANAGER`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`name`、`visibility`（`ALL`/`AUTHORIZED`）、`managerCasIds`（至少一人）、`accessGrants`（可为空数组）必填；`budget`、`fundingSource`、`paperRequired`、`ruleSetVersionId`、`startAt`、`endAt` 可选。</li>
     *   <li>成功：`201`，`data.status` 为 `DRAFT`。</li>
     *   <li>常见错误：`10000` 起止时间颠倒；`32006` 授权对象不是本社团在任成员；`53000` `ruleSetVersionId` 不存在或未生效。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<ProjectVO>> create(Authentication authentication,
                                                     @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(projectService.create(authentication.getName(), request)));
    }

    /**
     * 获取指定项目的详情。
     *
     * <p>返回项目基本信息、负责人、授权范围与乐观锁 `version`。`visibility=AUTHORIZED` 的项目只对有查看权限的人开放。
     *
     * <ul>
     *   <li>权限：`visibility=ALL` 时任意社团成员；否则需 `CLUB_ADMIN`、项目负责人，或 `VIEW`/`SUBMIT`/`REVIEW`/`MANAGE` 任一项目范围。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`40000` 项目不存在；`20003` 无查看权限。</li>
     * </ul>
     */
    @GetMapping("/{projectId}")
    public Result<ProjectVO> detail(@PathVariable String projectId) {
        return Result.ok(projectService.detail(projectId));
    }

    /**
     * 更新指定项目。
     *
     * <p>修改项目基本信息、负责人与授权范围。项目状态不能在这里改，只能用 `open`、`stop-collection`、`start-organizing`、`archive` 等语义化接口。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；其余字段只提交需要修改的部分；`managerCasIds`、`accessGrants` 一旦提交即为整体替换；清空可空字段请用对应的 `clearXxx` 开关。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`40001` 项目已归档；`32006` 授权对象非法；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/{projectId}")
    public Result<ProjectVO> update(@PathVariable String projectId, Authentication authentication,
                                    @Valid @RequestBody UpdateProjectRequest request) {
        return Result.ok(projectService.update(projectId, authentication.getName(), request));
    }

    /**
     * 开放指定项目的发票收集。
     *
     * <p>把项目从 `DRAFT` 或 `COLLECTION_STOPPED` 推进到 `COLLECTING`，社员此时才能提交申请。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`（项目当前乐观锁版本）。</li>
     *   <li>成功：`200`，`data.status` 为 `COLLECTING`。</li>
     *   <li>常见错误：`40001` 当前状态不允许；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{projectId}/open")
    public Result<ProjectVO> open(@PathVariable String projectId, Authentication authentication,
                                  @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.open(projectId, authentication.getName(), request));
    }

    /**
     * 停止指定项目的发票收集。
     *
     * <p>把 `COLLECTING` 的项目改为 `COLLECTION_STOPPED`，停止新申请；已提交的内容仍可继续审核。可以再次 `open` 恢复收集。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `COLLECTION_STOPPED`。</li>
     *   <li>常见错误：`40001` 项目不在 `COLLECTING`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{projectId}/stop-collection")
    public Result<ProjectVO> stopCollection(@PathVariable String projectId, Authentication authentication,
                                            @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.stopCollection(projectId, authentication.getName(), request));
    }

    /**
     * 将指定项目转入整理阶段。
     *
     * <p>把 `COLLECTION_STOPPED` 的项目改为 `ORGANIZING`。进入整理后不能再创建或发布表单。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `ORGANIZING`。</li>
     *   <li>常见错误：`40001` 项目不在 `COLLECTION_STOPPED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{projectId}/start-organizing")
    public Result<ProjectVO> startOrganizing(@PathVariable String projectId, Authentication authentication,
                                             @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.startOrganizing(projectId, authentication.getName(), request));
    }

    /**
     * 归档指定项目。
     *
     * <p>把 `ORGANIZING` 的项目改为 `ARCHIVED` 并记录归档时间。归档后项目只读，通用编辑接口也会被拒绝。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `ARCHIVED`。</li>
     *   <li>常见错误：`40001` 项目不在 `ORGANIZING`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/{projectId}/archive")
    public Result<ProjectVO> archive(@PathVariable String projectId, Authentication authentication,
                                     @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.archive(projectId, authentication.getName(), request));
    }
}
