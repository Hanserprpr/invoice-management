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

    /**
     * 获取当前用户可访问的组织列表。
     *
     * <p>返回当前登录用户存在成员关系的全部社团，是调用任何租户接口前选择 `X-Organization-Id` 的入口。
     *
     * <ul>
     *   <li>权限：任意已登录用户，结果按本人成员关系过滤。</li>
     *   <li>请求头：本接口不需要 `X-Organization-Id`，租户过滤器对其放行。</li>
     *   <li>成功：`200`，`data` 为社团数组；没有任何成员关系时返回空数组。</li>
     *   <li>常见错误：`20000` 未登录。</li>
     * </ul>
     */
    @GetMapping
    public Result<List<OrganizationVO>> list(Authentication authentication) {
        return Result.ok(organizationService.listForUser(authentication.getName()));
    }

    /**
     * 获取指定组织的详情。
     *
     * <p>返回社团名称、类型、状态与乐观锁 `version`。路径 `id` 必须与当前租户头一致，不能借此读取其他社团。
     *
     * <ul>
     *   <li>权限：该社团的成员。</li>
     *   <li>请求头：`X-Organization-Id` 必填，且必须等于路径 `id`。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`20004` 路径与租户头不一致；`31000` 社团不存在。</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public Result<OrganizationVO> get(@PathVariable String id) {
        return Result.ok(organizationService.get(id));
    }
}
