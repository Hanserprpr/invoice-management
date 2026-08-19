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
     */
    @GetMapping("/projects/{projectId}/forms")
    public Result<List<ApplicationFormVO>> list(@PathVariable String projectId) {
        return Result.ok(formService.list(projectId));
    }

    /**
     * 在指定项目中创建申报表单。
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
     */
    @GetMapping("/forms/{formId}")
    public Result<ApplicationFormVO> detail(@PathVariable String formId) {
        return Result.ok(formService.detail(formId));
    }

    /**
     * 更新指定申报表单。
     */
    @PatchMapping("/forms/{formId}")
    public Result<ApplicationFormVO> update(@PathVariable String formId, Authentication authentication,
                                            @Valid @RequestBody UpdateFormRequest request) {
        return Result.ok(formService.update(formId, authentication.getName(), request));
    }

    /**
     * 发布指定申报表单的新版本。
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
     */
    @PostMapping("/forms/{formId}/pause")
    public Result<ApplicationFormVO> pause(@PathVariable String formId, Authentication authentication,
                                           @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.pause(formId, authentication.getName(), request));
    }

    /**
     * 恢复指定申报表单的收集。
     */
    @PostMapping("/forms/{formId}/resume")
    public Result<ApplicationFormVO> resume(@PathVariable String formId, Authentication authentication,
                                            @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.resume(formId, authentication.getName(), request));
    }

    /**
     * 结束指定申报表单的收集。
     */
    @PostMapping("/forms/{formId}/end")
    public Result<ApplicationFormVO> end(@PathVariable String formId, Authentication authentication,
                                         @Valid @RequestBody FormVersionRequest request) {
        return Result.ok(formService.end(formId, authentication.getName(), request));
    }

    /**
     * 获取指定申报表单的版本列表。
     */
    @GetMapping("/forms/{formId}/versions")
    public Result<List<FormVersionVO>> versions(@PathVariable String formId) {
        return Result.ok(formService.versions(formId));
    }

    /**
     * 获取指定申报表单的特定版本。
     */
    @GetMapping("/forms/{formId}/versions/{versionNo}")
    public Result<FormVersionVO> version(@PathVariable String formId,
                                         @PathVariable @Min(1) int versionNo) {
        return Result.ok(formService.version(formId, versionNo));
    }
}
