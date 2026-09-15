package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAccessServiceTests {

    private final Map<String, Boolean> memberships = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();
    private final OrganizationMemberMapper organizationMemberMapper =
            proxy(OrganizationMemberMapper.class, (method, args) ->
                    memberships.getOrDefault(args[0] + ":" + args[1], false));
    private final UserMapper userMapper = proxy(UserMapper.class, (method, args) ->
            "selectById".equals(method) ? users.get(String.valueOf(args[0])) : null);
    private final TenantAccessService service =
            new TenantAccessService(organizationMemberMapper, userMapper);

    @Test
    void platformAdminHasAccessWithoutMembership() {
        users.put("root", User.builder().casId("root").status("ACTIVE")
                .isPlatformAdmin(true).build());
        assertThat(service.hasAccess("01K00000000000000000000001", "root")).isTrue();
    }

    @Test
    void disabledPlatformAdminHasNoAccessWithoutMembership() {
        users.put("disabled-root", User.builder().casId("disabled-root").status("DISABLED")
                .isPlatformAdmin(true).build());
        assertThat(service.hasAccess("01K00000000000000000000001", "disabled-root")).isFalse();
    }

    @Test
    void regularUserWithoutMembershipHasNoAccess() {
        users.put("outsider", User.builder().casId("outsider").status("ACTIVE")
                .isPlatformAdmin(false).build());
        assertThat(service.hasAccess("01K00000000000000000000001", "outsider")).isFalse();
    }

    @Test
    void activeMemberHasAccessWithoutPlatformFlag() {
        users.put("member", User.builder().casId("member").status("ACTIVE")
                .isPlatformAdmin(false).build());
        memberships.put("01K00000000000000000000001:member", true);
        assertThat(service.hasAccess("01K00000000000000000000001", "member")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxy, args);
                    }
                    return invocation.call(method.getName(), args == null ? new Object[0] : args);
                });
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] args);
    }
}
