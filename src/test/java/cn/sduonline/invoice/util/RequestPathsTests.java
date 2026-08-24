package cn.sduonline.invoice.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathsTests {
    @Test
    void stripsProxyPrefixAndKeepsPlainPathsUnchanged() {
        assertThat(RequestPaths.applicationPath(request("", "/api/actuator/health/liveness")))
                .isEqualTo("/api/actuator/health/liveness");
        assertThat(RequestPaths.applicationPath(request("/invoice", "/invoice/api/actuator/health/liveness")))
                .isEqualTo("/api/actuator/health/liveness");
        assertThat(RequestPaths.applicationPath(request("/invoice", "/invoice")))
                .isEqualTo("/");
        assertThat(RequestPaths.applicationPath(request("/invoice", "/other/path")))
                .isEqualTo("/other/path");
    }

    private MockHttpServletRequest request(String contextPath, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        request.setRequestURI(uri);
        return request;
    }
}
