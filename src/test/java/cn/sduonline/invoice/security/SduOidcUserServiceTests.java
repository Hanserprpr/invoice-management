package cn.sduonline.invoice.security;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SduOidcUserServiceTests {

    @Test
    void rejectsMissingUnknownAndDisabledCasIdentity() {
        assertThatThrownBy(() -> service(new HashMap<>(), claims(null, "姓名"), new AtomicReference<>())
                .loadUser(null)).isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("casID");
        assertThatThrownBy(() -> service(new HashMap<>(), claims("unknown", "姓名"), new AtomicReference<>())
                .loadUser(null)).isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("尚未开通");

        Map<String, User> users = new HashMap<>();
        users.put("disabled", User.builder().casId("disabled").name("姓名")
                .status("DISABLED").version(0L).build());
        assertThatThrownBy(() -> service(users, claims("disabled", "姓名"), new AtomicReference<>())
                .loadUser(null)).isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void trustedOidcNameIsSynchronizedForActiveUser() {
        Map<String, User> users = new HashMap<>();
        User active = User.builder().casId("active").name("旧姓名").status("ACTIVE").version(0L).build();
        users.put("active", active);
        AtomicReference<User> updated = new AtomicReference<>();

        OidcUser result = service(users, claims("active", "OIDC 姓名"), updated).loadUser(null);

        assertThat(result.getClaimAsString("casID")).isEqualTo("active");
        assertThat(updated.get().getName()).isEqualTo("OIDC 姓名");
        assertThat(updated.get().getLastLoginAt()).isNotNull();
    }

    private SduOidcUserService service(Map<String, User> users, OidcUser oidcUser,
                                       AtomicReference<User> updated) {
        UserMapper mapper = proxy(UserMapper.class, (method, args) -> switch (method) {
            case "selectById" -> users.get(String.valueOf(args[0]));
            case "updateById" -> { updated.set((User) args[0]); yield 1; }
            default -> null;
        });
        return new SduOidcUserService(mapper, request -> oidcUser);
    }

    private OidcUser claims(String casId, String name) {
        Map<String, Object> values = new HashMap<>();
        if (casId != null) values.put("casID", casId);
        values.put("name", name);
        return proxy(OidcUser.class, (method, args) -> switch (method) {
            case "getClaimAsString" -> values.get(String.valueOf(args[0]));
            case "getIssuer" -> URI.create("https://i.sdu.edu.cn/pass-api").toURL();
            case "getClaims", "getAttributes" -> values;
            case "getName" -> casId;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(),
                        args == null ? new Object[0] : args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] args) throws Exception;
    }
}
