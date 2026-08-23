package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.data.vo.CurrentUserVO;
import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.Map;

@RequestMapping("/auth")
@RestController
public class AuthController {

    private final UserMapper userMapper;

    public AuthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 获取统一身份认证的登录入口。
     *
     * <p>返回山东大学统一身份认证的跳转地址，前端在未登录或会话失效时把浏览器导向该地址完成 OIDC 登录。
     *
     * <ul>
     *   <li>权限：允许匿名访问。</li>
     *   <li>请求头：不需要 `X-Organization-Id`。</li>
     *   <li>成功：`200`，`data.url` 为 `/oauth2/authorization/sdu`；部署在反向代理路径前缀下时需自行拼接前缀。</li>
     *   <li>备注：登录成功后服务端重定向到 `app.security.oidc.success-url`，会话凭证是 `SESSION` Cookie。</li>
     * </ul>
     */
    @GetMapping("/login-url")
    public Result<Map<String, String>> loginUrl() {
        return Result.ok(Map.of("url", "/oauth2/authorization/sdu"));
    }

    /**
     * 获取当前会话的 CSRF 令牌。
     *
     * <p>供跨域本地前端读取令牌后，在写请求的 `X-XSRF-TOKEN` 请求头中回传。
     * 该接口仍要求已登录会话；令牌同时由安全框架写入 `XSRF-TOKEN` Cookie。
     *
     * <ul>
     *   <li>权限：任意已登录用户。</li>
     *   <li>请求头：不需要 `X-Organization-Id`。</li>
     *   <li>成功：`200`，`data.token` 为当前会话的 CSRF 令牌。</li>
     * </ul>
     */
    @GetMapping("/csrf")
    @Operation(summary = "获取 CSRF 令牌", description = "返回 data.token，供写请求通过 X-XSRF-TOKEN 头回传")
    public Result<Map<String, String>> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        return Result.ok(Map.of("token", csrfToken.getToken()));
    }

    /**
     * 获取当前已登录用户的身份与权限信息。
     *
     * <p>同时映射 `/auth/me` 与 `/auth/login/success`，返回当前会话对应的学号、姓名和是否平台管理员，供前端初始化。
     *
     * <ul>
     *   <li>权限：任意已登录用户。</li>
     *   <li>请求头：不需要 `X-Organization-Id`；社团列表请另行调用 `GET /api/organizations`。</li>
     *   <li>成功：`200`，`data` 为 `{casId, name, platformAdmin}`。</li>
     *   <li>常见错误：`20000` 未登录；`20001` 登录状态已失效。</li>
     * </ul>
     */
    @GetMapping({"/me", "/login/success"})
    public Result<CurrentUserVO> currentUser(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        String casId = oidcUser.getClaimAsString("casID");
        User user = userMapper.selectById(casId);
        return Result.ok(new CurrentUserVO(casId, user.getName(),
                Boolean.TRUE.equals(user.getIsPlatformAdmin())));
    }

    /**
     * 处理退出登录成功后的响应。
     *
     * <p>Spring Security 注销成功后的落地地址，返回统一响应体而不是重定向到页面。
     *
     * <ul>
     *   <li>权限：允许匿名访问。</li>
     *   <li>成功：`200`，`data` 为空。</li>
     *   <li>备注：注销动作本身是 `POST /logout`，由 Spring Security 处理，需要携带 CSRF 头。</li>
     * </ul>
     */
    @GetMapping("/logout/success")
    public Result<Void> logoutSuccess() {
        return Result.ok();
    }

}
