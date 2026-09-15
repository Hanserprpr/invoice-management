package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.AuthorizationMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationServiceTests {

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Boolean> permissions = new HashMap<>();
    private final UserMapper userMapper = proxy(UserMapper.class, (method, args) ->
            "selectById".equals(method) ? users.get(String.valueOf(args[0])) : null);
    private final AuthorizationMapper authorizationMapper = proxy(AuthorizationMapper.class,
            (method, args) -> {
                if ("hasPermission".equals(method)) {
                    return permissions.getOrDefault(
                            args[0] + ":" + args[1] + ":" + args[2], false);
                }
                return false;
            });
    private final AuthorizationService service = new AuthorizationService(userMapper, authorizationMapper);

    @Test
    void platformAdministratorMustBeActiveAndExplicitlyFlagged() {
        users.put("admin", User.builder().casId("admin").status("ACTIVE")
                .isPlatformAdmin(true).build());
        users.put("member", User.builder().casId("member").status("ACTIVE")
                .isPlatformAdmin(false).build());

        assertThatCode(() -> service.requirePlatformAdmin("admin")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requirePlatformAdmin("member"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void permissionAlwaysUsesCurrentTenantAndActor() {
        permissions.put("01K00000000000000000000001:member:member:manage", true);
        try (TenantContext.Scope ignored = TenantContext.open(
                "01K00000000000000000000001", "member")) {
            assertThatCode(() -> service.requirePermission("member:manage")).doesNotThrowAnyException();
            assertThatThrownBy(() -> service.requirePermission("role:manage"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void platformAdminBypassesClubPermissionChecks() {
        users.put("root", User.builder().casId("root").status("ACTIVE")
                .isPlatformAdmin(true).build());
        try (TenantContext.Scope ignored = TenantContext.open(
                "01K00000000000000000000001", "root")) {
            assertThat(service.isPlatformAdmin()).isTrue();
            assertThat(service.isClubAdmin()).isTrue();
            assertThat(service.hasPermission("member:manage")).isTrue();
            assertThat(service.canReviewProject("p1")).isTrue();
            assertThat(service.canManageProject("p1")).isTrue();
            assertThat(service.canViewProject("p1")).isTrue();
            assertThat(service.canSubmitProject("p1")).isTrue();
            assertThatCode(() -> service.requirePermission("role:manage"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> service.requireReviewProject("p1"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> service.requireProjectManage("p1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void disabledPlatformAdminDoesNotBypassClubPermissionChecks() {
        users.put("disabled-root", User.builder().casId("disabled-root").status("DISABLED")
                .isPlatformAdmin(true).build());
        try (TenantContext.Scope ignored = TenantContext.open(
                "01K00000000000000000000001", "disabled-root")) {
            assertThat(service.isPlatformAdmin()).isFalse();
            assertThat(service.isClubAdmin()).isFalse();
            assertThat(service.hasPermission("member:manage")).isFalse();
            assertThatThrownBy(() -> service.requirePermission("member:manage"))
                    .isInstanceOf(BusinessException.class);
        }
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
