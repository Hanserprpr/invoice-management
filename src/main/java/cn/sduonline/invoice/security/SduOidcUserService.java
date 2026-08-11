package cn.sduonline.invoice.security;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.UserMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

/**
 * 校验山东大学 OIDC 用户，并同步非敏感的登录信息。
 */
@Service
public class SduOidcUserService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String CAS_ID_CLAIM = "casID";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserMapper userMapper;

    @Autowired
    public SduOidcUserService(UserMapper userMapper) {
        this(userMapper, new OidcUserService());
    }

    SduOidcUserService(UserMapper userMapper,
                       OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.userMapper = userMapper;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String casId = oidcUser.getClaimAsString(CAS_ID_CLAIM);
        if (casId == null || casId.isBlank()) {
            throw authenticationFailure("missing_cas_id", "OIDC 响应缺少 casID");
        }

        User user = userMapper.selectById(casId);
        if (user == null) {
            throw authenticationFailure("user_not_registered", "该学工号尚未开通系统账号");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw authenticationFailure("user_disabled", "账号已被禁用或已离任");
        }

        String name = oidcUser.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        user.setLastLoginAt(Instant.now());
        userMapper.updateById(user);
        return oidcUser;
    }

    private OAuth2AuthenticationException authenticationFailure(
            String code,
            String description
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(code, description, null),
                description
        );
    }
}
