package cn.sduonline.invoice.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SpaCsrfTokenRequestHandlerTests {
    private final SecurityErrorWriter errors = new SecurityErrorWriter(new ObjectMapper());

    private CsrfFilter filter(boolean compatible) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("None").secure(true));
        CsrfFilter filter = new CsrfFilter(repository);
        if (compatible) filter.setRequestHandler(new SpaCsrfTokenRequestHandler());
        filter.setAccessDeniedHandler(errors);
        return filter;
    }

    private Tokens issue(CsrfFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] masked = new String[1];
        filter.doFilter(request, response, (req, res) -> {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            masked[0] = token.getToken();
        });
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
        assertThat(masked[0]).isNotEqualTo(cookie.getValue());
        return new Tokens(cookie, masked[0]);
    }

    private void submit(CsrfFilter filter, String method, Cookie cookie, String header,
                        String parameter, boolean allowed) throws Exception {
        // Exercise the real CSRF filter, without spring-security-test's csrf()
        // shortcut that would hide cookie/header integration defects.
        MockHttpServletRequest request = new MockHttpServletRequest(method,
                "/api/projects/project-id/forms");
        if (cookie != null) request.setCookies(cookie);
        if (header != null) request.addHeader("X-XSRF-TOKEN", header);
        if (parameter != null) request.setParameter("_csrf", parameter);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedApplication = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reachedApplication.set(true));
        assertThat(reachedApplication.get()).isEqualTo(allowed);
        assertThat(response.getStatus()).isEqualTo(allowed ? 200 : 403);
        if (!allowed) {
            var body = new ObjectMapper().readTree(response.getContentAsString());
            assertThat(body.get("code").asInt()).isEqualTo(20006);
            assertThat(body.get("msg").asText()).isEqualTo("安全校验失败，请刷新后重试");
        }
    }

    @Test
    void reproducesDefaultHandlerRejectingRawCookieHeader() throws Exception {
        CsrfFilter original = filter(false);
        Tokens tokens = issue(original);
        submit(original, "POST", tokens.cookie(), tokens.cookie().getValue(), null, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void acceptsCookieAndExistingJsonTokenHeaders(String method) throws Exception {
        CsrfFilter filter = filter(true);
        Tokens tokens = issue(filter);
        submit(filter, method, tokens.cookie(), tokens.cookie().getValue(), null, true);
        submit(filter, method, tokens.cookie(), tokens.masked(), null, true);
    }

    @Test
    void rejectsMissingInvalidAndStaleTokens() throws Exception {
        CsrfFilter filter = filter(true);
        Tokens current = issue(filter);
        Tokens old = issue(filter);
        submit(filter, "POST", current.cookie(), null, null, false);
        submit(filter, "POST", current.cookie(), "invalid-token", null, false);
        submit(filter, "POST", null, current.cookie().getValue(), null, false);
        submit(filter, "POST", current.cookie(), old.cookie().getValue(), null, false);
        submit(filter, "POST", current.cookie(), old.masked(), null, false);
    }

    @Test
    void keepsMaskedFormParametersAndRejectsRawParameters() throws Exception {
        CsrfFilter filter = filter(true);
        Tokens tokens = issue(filter);
        submit(filter, "POST", tokens.cookie(), null, tokens.masked(), true);
        submit(filter, "POST", tokens.cookie(), null, tokens.cookie().getValue(), false);
        submit(filter, "POST", tokens.cookie(), "invalid-token", tokens.masked(), false);
    }

    @Test
    void getIssuesCookieEvenWhenControllerDoesNotReadToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter(true).doFilter(new MockHttpServletRequest("GET", "/api/projects"), response,
                (req, res) -> { });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull();
    }

    @Test
    void realPermissionDenialsKeepExistingCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        errors.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("code").asInt())
                .isEqualTo(20003);
    }

    private record Tokens(Cookie cookie, String masked) { }
}
