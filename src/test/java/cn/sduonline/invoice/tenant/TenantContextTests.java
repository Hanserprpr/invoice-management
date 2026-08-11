package cn.sduonline.invoice.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextTests {

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void exposesTenantInsideScopeAndClearsAfterClose() {
        try (TenantContext.Scope ignored = TenantContext.open("01K00000000000000000000000", "20260001")) {
            assertEquals("01K00000000000000000000000", TenantContext.requireOrganizationId());
            assertEquals("20260001", TenantContext.requireCasId());
        }

        assertNull(TenantContext.getNullable());
    }

    @Test
    void rejectsMissingOrNestedContext() {
        assertThrows(IllegalStateException.class, TenantContext::requireOrganizationId);

        try (TenantContext.Scope ignored = TenantContext.open("01K00000000000000000000000", "20260001")) {
            assertThrows(IllegalStateException.class,
                    () -> TenantContext.open("01K11111111111111111111111", "20260002"));
        }
    }
}
