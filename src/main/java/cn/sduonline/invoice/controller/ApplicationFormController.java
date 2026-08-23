package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.FormDtos.CopyFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.CreateFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.FormVersionRequest;
import cn.sduonline.invoice.data.dto.FormDtos.UpdateFormRequest;
import cn.sduonline.invoice.data.vo.ApplicationFormVO;
import cn.sduonline.invoice.data.vo.FormVersionVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.ApplicationFormService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class ApplicationFormController {
    private final ApplicationFormService formService;

    public ApplicationFormController(ApplicationFormService formService) {
        this.formService = formService;
    }

    /**
     * 获取指定项目的申报表单列表。
     *
     * <p>按创建时间倒序列出项目下的全部表单及其状态与乐观锁 `version`，用于表单管理页。社员侧的表单入口是 `GET /api/application-forms`。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 为表单数组。</li>
     *   <li>常见错误：`40000` 项目不存在；`20003` 无项目管理权限。</li>
     * </ul>
     */
    @GetMapping("/projects/{projectId}/forms")
    public Result<List<ApplicationFormVO>> list(@PathVariable String projectId) {
        return Result.ok(formService.list(projectId));
    }

    /**
     * 在指定项目中创建申报表单。
     *
     * <p>创建 `DRAFT` 状态的表单，字段结构写入草稿 schema；必须再调用发布接口生成不可变版本后，社员才看得到。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`name`、`submissionScope`（`ALL_MEMBERS`/`PROJECT_AUTHORIZED`）、`maxSubmissionsPerUser`（1–100）、`schema` 必填；`startsAt`、`endsAt` 可选。</li>
     *   <li>结构约束：`schema.fields` 最多 100 个；`key` 需匹配 `^[a-z][a-z0-9_]{0,63}$`；`visibleWhen`/`requiredWhen` 只支持 `EQUALS`、`NOT_EQUALS`、`IN`、`NOT_EMPTY` 这类结构化条件，不接受脚本或表达式。</li>
     *   <li>成功：`201`，`data.status` 为 `DRAFT`。</li>
     *   <li>常见错误：`10000` 表单结构或起止时间非法；`40001` 项目处于 `ORGANIZING` 或 `ARCHIVED`。</li>
     * </ul>
     */
    @PostMapping("/projects/{projectId}/forms")
    public ResponseEntity<Result<ApplicationFormVO>> create(
            @PathVariable String projectId, Authentication authentication,
            @Valid @RequestBody CreateFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(formService.create(projectId, authentication.getName(), request)));
    }

    /**
     * 复制已有表单到指定项目。
     *
     * <p>取源表单最新已发布版本的字段结构，在目标项目中新建一份草稿。提交范围与每人次数一并复制，起止时间清空。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。源表单所在项目同样需要该权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`sourceFormId`、`name` 必填。</li>
     *   <li>成功：`201`，副本状态为 `DRAFT`。</li>
     *   <li>常见错误：`41000` 源表单不存在；`41001` 源表单尚无已发布版本；`40001` 目标项目处于 `ORGANIZING` 或 `ARCHIVED`。</li>
     * </ul>
     */
    @PostMapping("/projects/{projectId}/forms/copy")
    public ResponseEntity<Result<ApplicationFormVO>> copy(
            @PathVariable String projectId, Authentication authentication,
            @Valid @RequestBody CopyFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(formService.copy(projectId, authentication.getName(), request)));
    }

    /**
     * 获取指定申报表单的详情。
     *
     * <p>返回表单的草稿结构、提交范围、时间窗口、每人次数与乐观锁 `version`，供表单编辑器加载。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`41000` 表单不存在；`20003` 无项目管理权限。</li>
     * </ul>
     */
    @GetMapping("/forms/{formId}")
    public Result<ApplicationFormVO> detail(@PathVariable String formId) {
        return Result.ok(formService.detail(formId));
    }

    /**
     * 更新指定申报表单。
     *
     * <p>只改草稿：字段结构改完后仍需发布新版本才会生效，已发布版本和基于它创建的申请都不受影响。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`name`、`submissionScope`、`maxSubmissionsPerUser`、`schema` 可选；清空时间窗口用 `clearStartsAt`、`clearEndsAt`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10001` 表单已 `ENDED`；`40001` 项目处于 `ORGANIZING` 或 `ARCHIVED`；`10000` 结构非法；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/forms/{formId}")
    public Result<ApplicationFormVO> update(@PathVariable String formId, Authentication authentication,
                                            @Valid @RequestBody UpdateFormRequest request) {
        return Result.ok(formService.update(formId, authentication.getName(), request));
    }

    /**
     * 发布指定申报表单的新版本。
     *
     * <p>把当前草稿结构固化成不可变的表单版本，表单转入 `PUBLISHED`，同时快照当时的字典项。历史版本没有修改或删除接口。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`（表单当前乐观锁版本）。</li>
     *   <li>成功：`201`，`data` 为新版本，`versionNo` 从 `1` 开始递增。</li>
     *   <li>常见错误：`10001` 表单已 `ENDED`，或草稿结构与上一版本完全相同；`10000` 草稿未通过发布前的完整校验；`40001` 项目状态不允许；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/forms/{formId}/publish")
    public ResponseEntity<Result<FormVersionVO>> publish(
            @PathVariable String formId, Authentication authentication,
            @Valid @RequestBody FormVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(formService.publish(formId, authentication.getName(), request)));
    }

    /**
     * 暂停指定申报表单的收集。
     *
     * <p>把 `PUBLISHED` 的表单改为 `PAUSED`，暂时不接受新的申请草稿；已有草稿不受影响。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `PAUSED`。</li>
     *   <li>常见错误：`10001` 表单不在 `PUBLISHED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/forms/{formId}/pause")
    public Result<ApplicationFormVO> pause(@PathVariable String formId, Authentication authentication,
                                           @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.pause(formId, authentication.getName(), request));
    }

    /**
     * 恢复指定申报表单的收集。
     *
     * <p>把 `PAUSED` 的表单改回 `PUBLISHED`。恢复时会重新检查项目状态，整理中或已归档的项目不能恢复收集。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `PUBLISHED`。</li>
     *   <li>常见错误：`10001` 表单不在 `PAUSED`；`40001` 项目处于 `ORGANIZING` 或 `ARCHIVED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/forms/{formId}/resume")
    public Result<ApplicationFormVO> resume(@PathVariable String formId, Authentication authentication,
                                            @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.resume(formId, authentication.getName(), request));
    }

    /**
     * 结束指定申报表单的收集。
     *
     * <p>把 `PUBLISHED` 或 `PAUSED` 的表单改为 `ENDED`。这是终态，之后不能再编辑、发布或恢复。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>成功：`200`，`data.status` 为 `ENDED`。</li>
     *   <li>常见错误：`10001` 表单不在 `PUBLISHED` 或 `PAUSED`；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/forms/{formId}/end")
    public Result<ApplicationFormVO> end(@PathVariable String formId, Authentication authentication,
                                         @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.end(formId, authentication.getName(), request));
    }

    /**
     * 获取指定申报表单的版本列表。
     *
     * <p>列出该表单已发布的全部不可变版本及发布人、发布时间，用于比对历史结构。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，尚未发布过时为空数组。</li>
     *   <li>常见错误：`41000` 表单不存在。</li>
     * </ul>
     */
    @GetMapping("/forms/{formId}/versions")
    public Result<List<FormVersionVO>> versions(@PathVariable String formId) {
        return Result.ok(formService.versions(formId));
    }

    /**
     * 获取指定申报表单的特定版本。
     *
     * <p>按版本号读取某个已发布版本的完整结构与字典快照，用于查看历史申请当时看到的表单。
     *
     * <ul>
     *   <li>权限：`CLUB_ADMIN`、项目负责人，或该项目 `MANAGE` 范围成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：路径 `versionNo` 为不小于 `1` 的整数。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`41001` 该版本号不存在；`10000` `versionNo` 小于 `1`。</li>
     * </ul>
     */
    @GetMapping("/forms/{formId}/versions/{versionNo}")
    public Result<FormVersionVO> version(@PathVariable String formId,
                                         @PathVariable @Min(1) int versionNo) {
        return Result.ok(formService.version(formId, versionNo));
    }
}
