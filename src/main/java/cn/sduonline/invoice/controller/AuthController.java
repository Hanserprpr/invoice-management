package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.data.vo.CurrentUserVO;
import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequestMapping("/auth")
@RestController
public class AuthController {

    private final UserMapper userMapper;

    public AuthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/login-url")
    public Result<Map<String, String>> loginUrl() {
        return Result.ok(Map.of("url", "/oauth2/authorization/sdu"));
    }

    @GetMapping({"/me", "/login/success"})
    public Result<CurrentUserVO> currentUser(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        String casId = oidcUser.getClaimAsString("casID");
        User user = userMapper.selectById(casId);
        return Result.ok(new CurrentUserVO(casId, user.getName(),
                Boolean.TRUE.equals(user.getIsPlatformAdmin())));
    }

    @GetMapping("/logout/success")
    public Result<Void> logoutSuccess() {
        return Result.ok();
    }

}
