package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.OrganizationVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.OrganizationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public Result<List<OrganizationVO>> list(Authentication authentication) {
        return Result.ok(organizationService.listForUser(authentication.getName()));
    }

    @GetMapping("/{id}")
    public Result<OrganizationVO> get(@PathVariable String id) {
        return Result.ok(organizationService.get(id));
    }
}
