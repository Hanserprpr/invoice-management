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

    @GetMapping("/application-forms")
    public Result<List<AvailableFormVO>> availableForms() {
        return Result.ok(applicationService.availableForms());
    }

    @GetMapping("/application-forms/{formId}")
    public Result<AvailableFormVO> availableForm(@PathVariable String formId) {
        return Result.ok(applicationService.availableForm(formId));
    }

    @PostMapping("/application-forms/{formId}/applications")
    public ResponseEntity<Result<ApplicationVO>> createDraft(
            @PathVariable String formId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(applicationService.createDraft(formId, authentication.getName())));
    }

    @GetMapping("/applications")
    public Result<PageResult<ApplicationVO>> listMine(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(applicationService.listMine(page, pageSize, status));
    }

    @GetMapping("/applications/{applicationId}")
    public Result<ApplicationVO> detail(@PathVariable String applicationId) {
        return Result.ok(applicationService.detail(applicationId));
    }

    @PatchMapping("/applications/{applicationId}")
    public Result<ApplicationVO> saveDraft(
            @PathVariable String applicationId, Authentication authentication,
            @Valid @RequestBody SaveDraftRequest request) {
        return Result.ok(applicationService.saveDraft(
                applicationId, authentication.getName(), request));
    }

    @PostMapping("/applications/{applicationId}/submit")
    public Result<ApplicationVO> submit(
            @PathVariable String applicationId, Authentication authentication,
            @Valid @RequestBody SubmitRequest request) {
        return Result.ok(applicationService.submit(applicationId, authentication.getName(), request));
    }

    @GetMapping("/applications/{applicationId}/revisions")
    public Result<List<ApplicationRevisionVO>> revisions(@PathVariable String applicationId) {
        return Result.ok(applicationService.revisions(applicationId));
    }
}
