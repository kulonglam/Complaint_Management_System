package com.cms.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
        @DefaultValue("") String url,
        @DefaultValue("") String serviceRoleKey
) {

    public boolean configured() {
        return url != null && !url.isBlank() && !url.contains("YOUR_PROJECT")
                && serviceRoleKey != null && !serviceRoleKey.isBlank();
    }
}
