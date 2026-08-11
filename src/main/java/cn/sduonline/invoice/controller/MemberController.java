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

    @GetMapping
    public Result<PageResult<MemberVO>> list(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(memberService.list(organizationId, page, pageSize));
    }

    @PostMapping
    public ResponseEntity<Result<MemberVO>> create(
            @PathVariable String organizationId,
            Authentication authentication,
            @Valid @RequestBody CreateMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(memberService.create(organizationId, authentication.getName(), request)));
    }

    @PatchMapping("/{casId}")
    public Result<MemberVO> update(@PathVariable String organizationId,
                                   @PathVariable String casId,
                                   Authentication authentication,
                                   @Valid @RequestBody UpdateMemberRequest request) {
        return Result.ok(memberService.update(organizationId, casId, authentication.getName(), request));
    }

    @PutMapping("/{casId}/roles")
    public Result<MemberVO> replaceRoles(@PathVariable String organizationId,
                                         @PathVariable String casId,
                                         Authentication authentication,
                                         @Valid @RequestBody ReplaceRolesRequest request) {
        return Result.ok(memberService.replaceRoles(organizationId, casId,
                authentication.getName(), request));
    }
}
