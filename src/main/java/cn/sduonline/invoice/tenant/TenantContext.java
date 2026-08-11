package cn.sduonline.invoice.tenant;

/**
 * 当前请求的租户上下文。
 *
 * <p>上下文使用 ThreadLocal 保存，必须通过 {@link Scope} 或 finally 清理，
 * 防止线程池复用造成跨租户数据泄露。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Scope open(String organizationId, String casId) {
        if (CONTEXT.get() != null) {
            throw new IllegalStateException("租户上下文已存在");
        }
        CONTEXT.set(new TenantInfo(organizationId, casId));
        return new Scope();
    }

    public static String requireOrganizationId() {
        TenantInfo tenantInfo = CONTEXT.get();
        if (tenantInfo == null) {
            throw new IllegalStateException("当前线程未设置租户上下文");
        }
        return tenantInfo.organizationId();
    }

    public static String requireCasId() {
        TenantInfo tenantInfo = CONTEXT.get();
        if (tenantInfo == null) {
            throw new IllegalStateException("当前线程未设置租户上下文");
        }
        return tenantInfo.casId();
    }

    public static TenantInfo getNullable() {
        return CONTEXT.get();
    }

    static void clear() {
        CONTEXT.remove();
    }

    public record TenantInfo(String organizationId, String casId) {
    }

    public static final class Scope implements AutoCloseable {

        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (!closed) {
                TenantContext.clear();
                closed = true;
            }
        }
    }
}
