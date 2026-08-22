package cn.sduonline.invoice.security;

import cn.sduonline.invoice.config.IdempotencyProperties;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.service.IdempotencyService;
import cn.sduonline.invoice.service.IdempotencyService.Acquired;
import cn.sduonline.invoice.tenant.TenantContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyFilterTests {

    @Test
    void downstreamExceptionReleasesAcquiredClaim() throws Exception {
        IdempotencyService service = mock(IdempotencyService.class);
        SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
        when(service.claim(any())).thenReturn(new Acquired("claim-id"));
        IdempotencyFilter filter = filter(service, errorWriter, 1024);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.addHeader("Idempotency-Key", "exception-key");
        request.setContentType("application/json");
        request.setContent("{\"value\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setUserPrincipal((Principal) () -> "exception-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (TenantContext.Scope ignored = TenantContext.open(
                "01KAP000000000000000000599", "exception-user")) {
            assertThatThrownBy(() -> filter.doFilter(request, response,
                    (req, res) -> { throw new ServletException("boom"); }))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("boom");
        }

        verify(service).release("claim-id");
    }

    @Test
    void oversizedRequestIsRejectedBeforeClaiming() throws Exception {
        IdempotencyService service = mock(IdempotencyService.class);
        SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
        IdempotencyFilter filter = filter(service, errorWriter, 4);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.addHeader("Idempotency-Key", "large-key");
        request.setContent("12345".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setUserPrincipal((Principal) () -> "large-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        verify(errorWriter).write(response, 413, BizCode.PARAM_INVALID);
        verify(service, never()).claim(any());
    }

    @Test
    void commitFailureDoesNotExposeBufferedSuccessResponse() throws Exception {
        IdempotencyService service = mock(IdempotencyService.class);
        SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
        when(service.claim(any())).thenReturn(new Acquired("claim-id"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new IllegalStateException("commit failed"))
                .when(transactionManager).commit(any());
        IdempotencyFilter filter = filter(service, errorWriter, transactionManager, 1024);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.addHeader("Idempotency-Key", "commit-key");
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setUserPrincipal((Principal) () -> "commit-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":true}");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");

        org.assertj.core.api.Assertions.assertThat(response.getContentAsByteArray()).isEmpty();
        verify(service).release("claim-id");
    }

    private IdempotencyFilter filter(IdempotencyService service, SecurityErrorWriter errorWriter,
                                     long maxRequestBytes) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return filter(service, errorWriter, transactionManager, maxRequestBytes);
    }

    private IdempotencyFilter filter(IdempotencyService service, SecurityErrorWriter errorWriter,
                                     PlatformTransactionManager transactionManager,
                                     long maxRequestBytes) {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setMaxRequestBytes(maxRequestBytes);
        return new IdempotencyFilter(service, errorWriter, transactionManager, properties);
    }
}
