package cn.sduonline.invoice.config;

import cn.sduonline.invoice.security.SduOidcUserService;
import cn.sduonline.invoice.security.SecurityErrorWriter;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.tenant.TenantContextFilter;
import cn.sduonline.invoice.security.RedisRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * 山东大学 OIDC 登录和接口访问控制。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String SDU_ISSUER = "https://i.sdu.edu.cn/pass-api";
    private static final String SDU_AUTHORIZATION_URI =
            "https://i.sdu.edu.cn/pass-api/auth/oidc/authorize";
    private static final String SDU_TOKEN_URI =
            "https://i.sdu.edu.cn/pass-api/auth/oidc/token";
    private static final String SDU_USER_INFO_URI =
            "https://i.sdu.edu.cn/pass-api/auth/oidc/userinfo";
    private static final String SDU_JWK_SET_URI =
            "https://i.sdu.edu.cn/pass-api/auth/oidc/jwks";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.security.oidc.client-id}") String clientId,
            @Value("${app.security.oidc.client-secret}") String clientSecret,
            @Value("${app.security.oidc.redirect-uri}") String redirectUri
    ) {
        ClientRegistration sdu = ClientRegistration.withRegistrationId("sdu")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile")
                .authorizationUri(SDU_AUTHORIZATION_URI)
                .tokenUri(SDU_TOKEN_URI)
                .userInfoUri(SDU_USER_INFO_URI)
                .userNameAttributeName("casID")
                .jwkSetUri(SDU_JWK_SET_URI)
                .issuerUri(SDU_ISSUER)
                .clientName("山东大学统一身份认证")
                .build();
        return new InMemoryClientRegistrationRepository(sdu);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SduOidcUserService sduOidcUserService,
            SecurityErrorWriter securityErrorWriter,
            TenantContextFilter tenantContextFilter,
            ObjectProvider<RedisRateLimitFilter> rateLimitFilter,
            @Value("${app.security.oidc.success-url}") String successUrl
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/auth/login-url",
                                "/auth/logout/success",
                                "/oauth2/**",
                                "/login/**",
                                "/error",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(sduOidcUserService))
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(successUrl))
                        .failureHandler((request, response, exception) ->
                                securityErrorWriter.write(response, 401, BizCode.TOKEN_INVALID))
                )
                .csrf(csrf -> csrf.csrfTokenRepository(
                        CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .addFilterAfter(tenantContextFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorWriter)
                        .accessDeniedHandler(securityErrorWriter)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/auth/logout/success")
                );
        rateLimitFilter.ifAvailable(filter -> http.addFilterBefore(filter,
                OAuth2AuthorizationRequestRedirectFilter.class));
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> disableTenantFilterAutoRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnBean(RedisRateLimitFilter.class)
    public FilterRegistrationBean<RedisRateLimitFilter> disableRateLimitFilterAutoRegistration(
            ObjectProvider<RedisRateLimitFilter> filter) {
        FilterRegistrationBean<RedisRateLimitFilter> registration = new FilterRegistrationBean<>();
        filter.ifAvailable(registration::setFilter);
        registration.setEnabled(false);
        return registration;
    }
}
