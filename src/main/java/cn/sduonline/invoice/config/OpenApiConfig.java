package cn.sduonline.invoice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（Swagger UI / OpenAPI）定义，仅在开启 springdoc.api-docs 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    static final String SESSION_COOKIE_SCHEME = "sessionCookie";

    @Bean
    public OpenAPI invoiceManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("社团发票收集管理辅助工具 API")
                        .version("v3.0")
                        .description("""
                                接口调用需先通过山东大学统一身份认证登录（GET /api/oauth2/authorization/sdu），\
                                浏览器会话 Cookie 即为凭证；写操作需要携带 CSRF 头 X-XSRF-TOKEN。"""))
                .components(new Components().addSecuritySchemes(SESSION_COOKIE_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")
                                .description("OIDC 登录后由服务端下发的会话 Cookie")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE_SCHEME));
    }
}
