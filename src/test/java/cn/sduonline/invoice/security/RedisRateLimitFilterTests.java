package cn.sduonline.invoice.security;

import cn.sduonline.invoice.config.RateLimitProperties;
import cn.sduonline.invoice.data.enums.BizCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimitFilterTests {
    @Test
    void permitsWithinLimitAndRejectsExcessAcrossSharedRedisCounter() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityErrorWriter writer = mock(SecurityErrorWriter.class);
        RateLimitProperties properties = properties();
        @SuppressWarnings("unchecked")
        RedisScript<Long> script = any(RedisScript.class);
        when(redis.execute(script, anyList(), any())).thenReturn(60L, 61L);
        RedisRateLimitFilter filter = new RedisRateLimitFilter(redis, properties, writer);

        MockHttpServletRequest allowed = request();
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(allowed, allowedResponse, chain);
        assertThat(chain.getRequest()).isNotNull();
        verify(writer, never()).write(any(), anyInt(), any());

        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(request(), rejectedResponse, new MockFilterChain());
        verify(writer).write(eq(rejectedResponse), eq(429), eq(BizCode.TOO_MANY_REQUESTS));
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityErrorWriter writer = mock(SecurityErrorWriter.class);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisRateLimitFilter filter = new RedisRateLimitFilter(redis, properties(), writer);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());
        verify(writer).write(eq(response), eq(503), eq(BizCode.THIRD_PARTY_UNAVAILABLE));
    }

    @Test
    void countsProxyPrefixedWriteRequestsAgainstTheSameRoute() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityErrorWriter writer = mock(SecurityErrorWriter.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(61L);
        RedisRateLimitFilter filter = new RedisRateLimitFilter(redis, properties(), writer);

        MockHttpServletRequest proxied = new MockHttpServletRequest("POST", "/invoice/api/files/uploads");
        proxied.setContextPath("/invoice");
        proxied.setRequestURI("/invoice/api/files/uploads");
        proxied.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(proxied, response, new MockFilterChain());

        verify(writer).write(eq(response), eq(429), eq(BizCode.TOO_MANY_REQUESTS));
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRequests(60);
        properties.setWindow(Duration.ofMinutes(1));
        return properties;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/files/uploads");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
