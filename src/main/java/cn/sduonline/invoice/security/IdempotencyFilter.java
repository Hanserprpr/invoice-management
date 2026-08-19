package cn.sduonline.invoice.security;

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
import org.springframework.stereotype.Component;
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

    public IdempotencyFilter(IdempotencyService service, SecurityErrorWriter errorWriter) {
        this.service = service;
        this.errorWriter = errorWriter;
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

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
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
        boolean downstreamCompleted = false;
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
            downstreamCompleted = true;
            byte[] body = cachedResponse.getContentAsByteArray();
            service.complete(claimId, cachedResponse.getStatus(),
                    new String(body, responseCharset(cachedResponse)));
        } catch (IOException | ServletException | RuntimeException exception) {
            if (!downstreamCompleted) {
                try {
                    service.release(claimId);
                } catch (RuntimeException releaseException) {
                    exception.addSuppressed(releaseException);
                }
            }
            throw exception;
        } finally {
            cachedResponse.copyBodyToResponse();
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

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
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
}
