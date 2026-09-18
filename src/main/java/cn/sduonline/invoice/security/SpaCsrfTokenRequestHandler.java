package cn.sduonline.invoice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** Supports raw cookie headers and the masked token returned by /api/auth/csrf. */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final XorCsrfTokenRequestAttributeHandler masked =
            new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        // Keep BREACH protection for tokens exposed through request attributes / JSON.
        masked.handle(request, response, csrfToken);
        // Materialize the deferred token so browser clients receive the cookie on GET.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        if (header != null && MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8),
                csrfToken.getToken().getBytes(StandardCharsets.UTF_8))) {
            return header;
        }
        // Existing clients send the masked JSON token in the same header. Form
        // parameters also keep their original masked-token semantics.
        return masked.resolveCsrfTokenValue(request, csrfToken);
    }
}
