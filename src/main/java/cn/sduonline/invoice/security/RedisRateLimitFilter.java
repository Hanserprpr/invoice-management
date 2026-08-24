package cn.sduonline.invoice.security;

import cn.sduonline.invoice.config.RateLimitProperties;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.util.RequestPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RedisRateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final SecurityErrorWriter errorWriter;

    public RedisRateLimitFilter(StringRedisTemplate redis, RateLimitProperties properties,
                                SecurityErrorWriter errorWriter) {
        this.redis = redis;
        this.properties = properties;
        this.errorWriter = errorWriter;
        if (properties.getRequests() < 1 || properties.getWindow() == null
                || properties.getWindow().isNegative() || properties.getWindow().isZero()) {
            throw new IllegalStateException("Rate limit requests and window must be positive");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = RequestPaths.applicationPath(request);
        if (path.startsWith("/api/actuator/")) return true;
        if (path.equals("/api/auth/login-url") || path.startsWith("/api/oauth2/")
                || path.startsWith("/api/login/oauth2/")) return false;
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        return !(path.startsWith("/api/files") || path.startsWith("/api/applications")
                || path.startsWith("/api/invoices") || path.startsWith("/api/export-batches")
                || path.startsWith("/api/platform/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Duration window = properties.getWindow();
        String identity = request.getSession(false) == null
                ? request.getRemoteAddr() : request.getSession(false).getId();
        String bucket = request.getMethod() + ":" + routeBucket(RequestPaths.applicationPath(request));
        String key = "invoice-management:rate:" + hash(identity) + ":" + hash(bucket);
        try {
            Long count = redis.execute(SCRIPT, List.of(key), Long.toString(window.toMillis()));
            if (count == null) throw new IllegalStateException("empty Redis response");
            if (count > properties.getRequests()) {
                response.setHeader("Retry-After", Long.toString(Math.max(1, window.toSeconds())));
                errorWriter.write(response, 429, BizCode.TOO_MANY_REQUESTS);
                return;
            }
        } catch (RuntimeException exception) {
            errorWriter.write(response, 503, BizCode.THIRD_PARTY_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }

    private String routeBucket(String path) {
        return path.replaceAll("/[0-9A-HJKMNP-TV-Z]{26}(?=/|$)", "/{id}")
                .replaceAll("/[^/]{1,64}(?=/roles$)", "/{casId}");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)), 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
