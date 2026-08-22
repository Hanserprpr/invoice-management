package cn.sduonline.invoice.security;

import cn.sduonline.invoice.config.IdempotencyProperties;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.service.IdempotencyService;
import cn.sduonline.invoice.service.IdempotencyService.Acquired;
import cn.sduonline.invoice.service.IdempotencyService.ClaimRequest;
import cn.sduonline.invoice.service.IdempotencyService.Replay;
import cn.sduonline.invoice.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.Set;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService service;
    private final SecurityErrorWriter errorWriter;
    private final TransactionTemplate transactionTemplate;
    private final long maxRequestBytes;

    public IdempotencyFilter(IdempotencyService service, SecurityErrorWriter errorWriter,
                             PlatformTransactionManager transactionManager,
                             IdempotencyProperties properties) {
        this.service = service;
        this.errorWriter = errorWriter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        if (properties.getMaxRequestBytes() < 1
                || properties.getMaxRequestBytes() >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("app.idempotency.max-request-bytes is out of range");
        }
        long timeoutSeconds = properties.getRequestTimeout().toSeconds();
        if (timeoutSeconds < 1 || timeoutSeconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("app.idempotency.request-timeout is out of range");
        }
        if (timeoutSeconds >= properties.getProcessingTtl().toSeconds()) {
            throw new IllegalArgumentException(
                    "app.idempotency.request-timeout must be shorter than processing-ttl");
        }
        this.transactionTemplate.setTimeout((int) timeoutSeconds);
        this.maxRequestBytes = properties.getMaxRequestBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || !MUTATING_METHODS.contains(request.getMethod().toUpperCase())
                || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(HEADER).trim();
        if (key.isEmpty() || key.length() > 100) {
            errorWriter.write(response, HttpServletResponse.SC_BAD_REQUEST, BizCode.PARAM_INVALID);
            return;
        }
        String path = requestTarget(request);
        if (path.length() > 500) {
            errorWriter.write(response, HttpServletResponse.SC_BAD_REQUEST, BizCode.PARAM_INVALID);
            return;
        }

        CachedBodyRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyRequest(request, maxRequestBytes);
        } catch (RequestBodyTooLargeException exception) {
            errorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(), BizCode.PARAM_INVALID);
            return;
        }
        TenantContext.TenantInfo tenant = TenantContext.getNullable();
        String organizationId = tenant == null ? null : tenant.organizationId();
        String scope = organizationId == null ? "GLOBAL" : "ORG:" + organizationId;
        IdempotencyService.ClaimOutcome outcome;
        try {
            outcome = service.claim(new ClaimRequest(scope, organizationId, principal.getName(), key,
                    request.getMethod().toUpperCase(), path, sha256(cachedRequest.body)));
        } catch (BusinessException exception) {
            errorWriter.write(response, exception.getStatus().value(), exception.getBizCode());
            return;
        }

        if (outcome instanceof Replay replay) {
            replay(response, replay);
            return;
        }

        String claimId = ((Acquired) outcome).recordId();
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean emitBufferedResponse = false;
        boolean claimSettled = false;
        try {
            claimSettled = Boolean.TRUE.equals(transactionTemplate.execute(ignored ->
                    executeDownstream(filterChain, cachedRequest, cachedResponse, claimId)));
            emitBufferedResponse = true;
        } catch (DownstreamException exception) {
            claimSettled = true;
            release(claimId, exception);
            if (exception.getCause() instanceof IOException ioException) throw ioException;
            if (exception.getCause() instanceof ServletException servletException) {
                throw servletException;
            }
            throw exception;
        } catch (UnexpectedRollbackException exception) {
            // 业务层 @Transactional 参与者抛出异常后会把外层事务标记为 rollback-only，
            // 直到这里提交时才暴露。此时下游已经写好了对应的错误响应，业务写入也已整体
            // 回滚，应当把这个真实响应发出去，而不是让容器用 500 覆盖它。
            claimSettled = true;
            release(claimId, exception);
            if (cachedResponse.getStatus() < HttpStatus.BAD_REQUEST.value()) throw exception;
            emitBufferedResponse = true;
        } catch (RuntimeException exception) {
            claimSettled = true;
            release(claimId, exception);
            throw exception;
        } finally {
            if (!claimSettled) release(claimId, null);
            if (emitBufferedResponse) {
                cachedResponse.copyBodyToResponse();
            }
        }
    }

    private boolean executeDownstream(FilterChain filterChain, CachedBodyRequest request,
                                      ContentCachingResponseWrapper response, String claimId) {
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= HttpStatus.BAD_REQUEST.value()) {
                // 失败响应不做记忆：客户端拿同一把幂等键重试时必须真正重新执行，
                // 而不是收到缓存下来的 4xx/5xx。占位记录由调用方释放。
                return false;
            }
            byte[] body = response.getContentAsByteArray();
            service.complete(claimId, response.getStatus(),
                    new String(body, responseCharset(response)));
            return true;
        } catch (IOException | ServletException exception) {
            throw new DownstreamException(exception);
        }
    }

    private void release(String claimId, RuntimeException original) {
        try {
            service.release(claimId);
        } catch (RuntimeException releaseException) {
            if (original == null) {
                logger.warn("释放幂等占位记录失败: " + claimId, releaseException);
            } else {
                original.addSuppressed(releaseException);
            }
        }
    }

    private void replay(HttpServletResponse response, Replay replay) throws IOException {
        response.setStatus(replay.responseStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (replay.responseBody() != null) {
            response.getOutputStream().write(replay.responseBody().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String requestTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
    }

    private Charset responseCharset(HttpServletResponse response) {
        String encoding = response.getCharacterEncoding();
        return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, long maxRequestBytes)
                throws IOException {
            super(request);
            if (request.getContentLengthLong() > maxRequestBytes) {
                throw new RequestBodyTooLargeException();
            }
            this.body = request.getInputStream().readNBytes((int) maxRequestBytes + 1);
            if (body.length > maxRequestBytes) {
                throw new RequestBodyTooLargeException();
            }
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    if (readListener == null) {
                        throw new IllegalArgumentException("readListener is required");
                    }
                    try {
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        } else {
                            readListener.onDataAvailable();
                        }
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class RequestBodyTooLargeException extends IOException {
    }

    private static final class DownstreamException extends RuntimeException {
        private DownstreamException(Exception cause) {
            super(cause);
        }
    }
}
