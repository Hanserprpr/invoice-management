package cn.sduonline.invoice.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求路径工具。反向代理带路径前缀（X-Forwarded-Prefix）时，
 * getRequestURI() 会包含该前缀，必须去掉后再与应用内部路径比较。
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    /** 返回不含上下文/转发前缀的应用内部路径。 */
    public static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !uri.startsWith(contextPath)) {
            return uri;
        }
        String path = uri.substring(contextPath.length());
        return path.isEmpty() ? "/" : path;
    }
}
