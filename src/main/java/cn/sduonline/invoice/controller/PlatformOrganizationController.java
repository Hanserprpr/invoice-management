package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.UpdateOrganizationRequest;
import cn.sduonline.invoice.data.vo.OrganizationVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/organizations")
public class PlatformOrganizationController {
    private final OrganizationService organizationService;

    public PlatformOrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * 创建组织。
     */
    @PostMapping
    public ResponseEntity<Result<OrganizationVO>> create(Authentication authentication,
                                                         @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(organizationService.create(authentication.getName(), request)));
    }

    /**
     * 更新指定组织的信息。
     */
    @PatchMapping("/{id}")
    public Result<OrganizationVO> update(Authentication authentication,
                                         @PathVariable String id,
                                         @Valid @RequestBody UpdateOrganizationRequest request) {
        return Result.ok(organizationService.update(authentication.getName(), id, request));
    }
}
