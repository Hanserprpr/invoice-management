package cn.sduonline.invoice.security;

import cn.sduonline.invoice.service.IdempotencyService;
import cn.sduonline.invoice.service.IdempotencyService.Acquired;
import cn.sduonline.invoice.tenant.TenantContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyFilterTests {

    @Test
    void downstreamExceptionReleasesAcquiredClaim() throws Exception {
        IdempotencyService service = mock(IdempotencyService.class);
        SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
        when(service.claim(any())).thenReturn(new Acquired("claim-id"));
        IdempotencyFilter filter = new IdempotencyFilter(service, errorWriter);

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
}
