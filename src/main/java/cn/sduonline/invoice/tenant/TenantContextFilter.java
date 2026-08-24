package cn.sduonline.invoice.tenant;

import cn.sduonline.invoice.config.ApiDocPaths;
import cn.sduonline.invoice.service.TenantAccessService;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.security.SecurityErrorWriter;
import cn.sduonline.invoice.util.RequestPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.regex.Pattern;

/**
 * 建立并清理当前 HTTP 请求的租户上下文。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Organization-Id";
    private static final Pattern ULID_PATTERN =
            Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$", Pattern.CASE_INSENSITIVE);

    private final TenantAccessService tenantAccessService;
    private final SecurityErrorWriter securityErrorWriter;

    public TenantContextFilter(TenantAccessService tenantAccessService,
                               SecurityErrorWriter securityErrorWriter) {
        this.tenantAccessService = tenantAccessService;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = RequestPaths.applicationPath(request);
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/api/error")
                || path.startsWith("/api/auth")
                || path.startsWith("/api/oauth2/")
                || path.startsWith("/api/login/")
                || path.equals("/api/logout")
                || path.startsWith("/api/actuator/")
                || ApiDocPaths.matches(path)
                || path.startsWith("/api/platform/")
                || path.equals("/api/organizations");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String organizationId = request.getHeader(TENANT_HEADER);
        if (organizationId == null || !ULID_PATTERN.matcher(organizationId).matches()) {
            securityErrorWriter.write(response, HttpServletResponse.SC_BAD_REQUEST,
                    BizCode.PARAM_INVALID);
            return;
        }

        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            securityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    BizCode.NOT_LOGIN);
            return;
        }

        String casId = principal.getName();
        if (!tenantAccessService.hasAccess(organizationId, casId)) {
            securityErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                    BizCode.CROSS_CLUB_FORBIDDEN);
            return;
        }

        try (TenantContext.Scope ignored = TenantContext.open(organizationId, casId)) {
            filterChain.doFilter(request, response);
        }
    }
}
