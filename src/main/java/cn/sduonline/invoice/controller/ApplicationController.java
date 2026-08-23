package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.vo.ApplicationRevisionVO;
import cn.sduonline.invoice.data.vo.ApplicationVO;
import cn.sduonline.invoice.data.vo.AvailableFormVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.ApplicationService;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class ApplicationController {
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 获取当前用户可申报的表单列表。
     *
     * <p>返回当前社团中正在收集、且本人处于提交范围内的已发布表单，是社员填报入口的表单选择列表。
     *
     * <ul>
     *   <li>权限：社团成员；`submissionScope=PROJECT_AUTHORIZED` 的表单还要求本人对该项目有 `SUBMIT` 或管理权限。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 为可填报表单数组，没有可填报表单时是空数组。</li>
     *   <li>常见错误：`10000` 缺少或格式错误的租户头；`20004` 不是该社团成员。</li>
     * </ul>
     */
    @GetMapping("/application-forms")
    public Result<List<AvailableFormVO>> availableForms() {
        return Result.ok(applicationService.availableForms());
    }

    /**
     * 获取指定可申报表单的详情。
     *
     * <p>以社员视角读取单张表单的最新已发布结构，用于渲染填报页；同时会校验表单是否仍在收集窗口内。
     *
     * <ul>
     *   <li>权限：社团成员，且在该表单的提交范围内。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`41000` 表单不存在；`41004` 表单已暂停或结束；`42002` 不在提交时间窗口内；`20003` 不在提交范围内。</li>
     * </ul>
     */
    @GetMapping("/application-forms/{formId}")
    public Result<AvailableFormVO> availableForm(@PathVariable String formId) {
        return Result.ok(applicationService.availableForm(formId));
    }

    /**
     * 根据指定表单创建申报草稿。
     *
     * <p>创建或复用申请草稿：本人在该表单下已有可编辑的申请时直接返回原记录，不会重复创建。草稿会永久绑定创建时的表单版本。
     *
     * <ul>
     *   <li>权限：社团成员，且在该表单的提交范围内。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：无。</li>
     *   <li>成功：`201`，`data.status` 为 `DRAFT`，`data.version` 为 `0`。</li>
     *   <li>常见错误：`42003` 已达到每人提交次数上限；`41004`／`42002` 表单状态或时间窗口不允许；`41001` 表单尚无已发布版本。</li>
     * </ul>
     */
    @PostMapping("/application-forms/{formId}/applications")
    public ResponseEntity<Result<ApplicationVO>> createDraft(
            @PathVariable String formId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(applicationService.createDraft(formId, authentication.getName())));
    }

    /**
     * 分页查询当前用户的申报记录。
     *
     * <p>按更新时间倒序列出本人在当前社团的全部申请。申请状态由其名下发票的状态派生，不能直接修改。
     *
     * <ul>
     *   <li>权限：本人记录，其他人的申请不会出现在结果里。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>请求参数：`page` 默认 `1`；`pageSize` 默认 `20`，上限 `100`；`status` 可选，取值 `DRAFT`、`SUBMITTED`、`PROCESSING`、`RETURNED`、`PARTIALLY_APPROVED`、`APPROVED`、`REJECTED`、`COMPLETED`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`10000` `status` 取值非法。</li>
     * </ul>
     */
    @GetMapping("/applications")
    public Result<PageResult<ApplicationVO>> listMine(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(applicationService.listMine(page, pageSize, status));
    }

    /**
     * 获取指定申报的详情。
     *
     * <p>返回申请的答案 JSON、绑定的表单版本、派生状态与乐观锁 `version`，用于继续填写或查看已提交内容。
     *
     * <ul>
     *   <li>权限：申报人本人；跨人访问一律按 `42000`／`50000` 处理，不泄露记录是否存在。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`42000` 申请不存在或不属于本人。</li>
     * </ul>
     */
    @GetMapping("/applications/{applicationId}")
    public Result<ApplicationVO> detail(@PathVariable String applicationId) {
        return Result.ok(applicationService.detail(applicationId));
    }

    /**
     * 保存指定申报的草稿内容。
     *
     * <p>自动保存用的整体覆盖式接口：每次成功保存都会追加一条修订记录，并按表单版本校验字段类型、条件显示、数值范围和人员归属。
     *
     * <ul>
     *   <li>权限：申报人本人；跨人访问一律按 `42000`／`50000` 处理，不泄露记录是否存在。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`answers`（完整答案 JSON，非增量）与 `version` 必填。</li>
     *   <li>成功：`200`，`data.version` 已自增。</li>
     *   <li>常见错误：`42001` 申请当前状态不可编辑；`10000` 答案不符合表单结构；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/applications/{applicationId}")
    public Result<ApplicationVO> saveDraft(
            @PathVariable String applicationId, Authentication authentication,
            @Valid @RequestBody SaveDraftRequest request) {
        return Result.ok(applicationService.saveDraft(
                applicationId, authentication.getName(), request));
    }

    /**
     * 提交指定申报。
     *
     * <p>正式提交：额外检查必填条件、项目与表单状态、时间窗口、提交范围、每人次数、附件就绪状态和发票归属，通过后在同一事务里把申请与其名下发票一起转为已提交。
     *
     * <ul>
     *   <li>权限：申报人本人；跨人访问一律按 `42000`／`50000` 处理，不泄露记录是否存在。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：只有 `version`。</li>
     *   <li>约束：答案里的发票 ID 集合必须与该申请下未作废的发票完全一致，多一张或少一张都会被拒绝。</li>
     *   <li>成功：`200`，`data.status` 为 `SUBMITTED`。</li>
     *   <li>常见错误：`42001` 状态不允许提交；`42002` 不在提交时间窗口内；`42003` 超出每人次数上限；`80000` 引用的文件尚未通过安全检测；`10000` 必填项缺失或发票集合不一致；`10007` 版本冲突。</li>
     * </ul>
     */
    @PostMapping("/applications/{applicationId}/submit")
    public Result<ApplicationVO> submit(
            @PathVariable String applicationId, Authentication authentication,
            @Valid @RequestBody SubmitRequest request) {
        return Result.ok(applicationService.submit(applicationId, authentication.getName(), request));
    }

    /**
     * 获取指定申报的修订历史。
     *
     * <p>按时间列出该申请每次自动保存与提交产生的答案修订，用于追溯填报过程。修订记录只追加，不可修改或删除。
     *
     * <ul>
     *   <li>权限：申报人本人；跨人访问一律按 `42000`／`50000` 处理，不泄露记录是否存在。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`，`data` 为修订数组。</li>
     *   <li>常见错误：`42000` 申请不存在或不属于本人。</li>
     * </ul>
     */
    @GetMapping("/applications/{applicationId}/revisions")
    public Result<List<ApplicationRevisionVO>> revisions(@PathVariable String applicationId) {
        return Result.ok(applicationService.revisions(applicationId));
    }
}
