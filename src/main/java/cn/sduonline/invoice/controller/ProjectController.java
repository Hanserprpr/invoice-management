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

    @GetMapping
    public Result<PageResult<ProjectVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(projectService.list(page, pageSize, status));
    }

    @PostMapping
    public ResponseEntity<Result<ProjectVO>> create(Authentication authentication,
                                                     @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(projectService.create(authentication.getName(), request)));
    }

    @GetMapping("/{projectId}")
    public Result<ProjectVO> detail(@PathVariable String projectId) {
        return Result.ok(projectService.detail(projectId));
    }

    @PatchMapping("/{projectId}")
    public Result<ProjectVO> update(@PathVariable String projectId, Authentication authentication,
                                    @Valid @RequestBody UpdateProjectRequest request) {
        return Result.ok(projectService.update(projectId, authentication.getName(), request));
    }

    @PostMapping("/{projectId}/open")
    public Result<ProjectVO> open(@PathVariable String projectId, Authentication authentication,
                                  @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.open(projectId, authentication.getName(), request));
    }

    @PostMapping("/{projectId}/stop-collection")
    public Result<ProjectVO> stopCollection(@PathVariable String projectId, Authentication authentication,
                                            @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.stopCollection(projectId, authentication.getName(), request));
    }

    @PostMapping("/{projectId}/start-organizing")
    public Result<ProjectVO> startOrganizing(@PathVariable String projectId, Authentication authentication,
                                             @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.startOrganizing(projectId, authentication.getName(), request));
    }

    @PostMapping("/{projectId}/archive")
    public Result<ProjectVO> archive(@PathVariable String projectId, Authentication authentication,
                                     @Valid @RequestBody ChangeStateRequest request) {
        return Result.ok(projectService.archive(projectId, authentication.getName(), request));
    }
}
