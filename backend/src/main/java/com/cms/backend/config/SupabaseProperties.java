package com.cms.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
        @DefaultValue("") String url,
        @DefaultValue("") String serviceRoleKey,
        @DefaultValue("") String jwtSecret
) {

    public boolean configured() {
        return url != null && !url.isBlank() && !url.contains("YOUR_PROJECT")
                && serviceRoleKey != null && !serviceRoleKey.isBlank();
    }

    public String issuer() {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.replaceAll("/+$", "") + "/auth/v1";
    }

    public String jwkSetUri() {
        String issuer = issuer();
        return issuer.isBlank() ? "" : issuer + "/.well-known/jwks.json";
    }

    public boolean hasJwtSecret() {
        return jwtSecret != null && !jwtSecret.isBlank();
    }
}
