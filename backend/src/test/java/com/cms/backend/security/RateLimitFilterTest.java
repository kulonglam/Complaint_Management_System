package com.cms.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RateLimitFilterTest {

    @Test
    void localWindowBlocksAfterCapacity() {
        AppProperties app = new AppProperties(
                "secret", "http://localhost:3000", true, "0 */15 * * * *",
                false, "noreply@localhost", 2, 60, "", ""
        );
        RateLimitFilter filter = new RateLimitFilter(app, new SupabaseProperties("", "", ""), emptyProvider());
        assertTrue(filter.allow("10.0.0.1"));
        assertTrue(filter.allow("10.0.0.1"));
        assertFalse(filter.allow("10.0.0.1"));
        assertTrue(filter.allow("10.0.0.2"));
    }

    private static ObjectProvider<SupabaseAdminClient> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public SupabaseAdminClient getObject() {
                return null;
            }

            @Override
            public SupabaseAdminClient getObject(Object... args) {
                return null;
            }

            @Override
            public SupabaseAdminClient getIfAvailable() {
                return null;
            }

            @Override
            public SupabaseAdminClient getIfUnique() {
                return null;
            }
        };
    }
}
