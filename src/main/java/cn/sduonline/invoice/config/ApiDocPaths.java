package cn.sduonline.invoice.config;

import java.util.List;

/**
 * springdoc 接口文档相关路径，供安全放行与租户过滤器共享。
 */
public final class ApiDocPaths {

    /** Spring Security 匹配用的路径模式。 */
    public static final List<String> PATTERNS = List.of(
            "/api/v3/api-docs", "/api/v3/api-docs/**", "/api/v3/api-docs.yaml",
            "/api/swagger-ui.html", "/api/swagger-ui/**");

    private ApiDocPaths() {
    }

    /** 判断请求路径是否属于接口文档，用于跳过租户等业务过滤器。 */
    public static boolean matches(String path) {
        return path.startsWith("/api/v3/api-docs") || path.startsWith("/api/swagger-ui");
    }
}
