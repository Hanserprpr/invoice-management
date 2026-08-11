package cn.sduonline.invoice.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import cn.sduonline.invoice.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MyBatis-Plus 插件配置。
 */
@Configuration
public class MybatisPlusConfig {

    private static final Set<String> GLOBAL_TABLES = Set.of(
            "user",
            "password_setup_token",
            "organization",
            "role",
            "permission",
            "role_permission"
    );

    /**
     * 启用基于 @Version 字段的乐观锁。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    private static final class TenantLineHandler
            implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

        @Override
        public Expression getTenantId() {
            return new StringValue(TenantContext.requireOrganizationId());
        }

        @Override
        public String getTenantIdColumn() {
            return "organization_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return GLOBAL_TABLES.contains(tableName.toLowerCase());
        }
    }
}
