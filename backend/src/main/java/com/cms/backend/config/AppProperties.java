package com.cms.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("change-me") String jobSecret,
        @DefaultValue("http://localhost:3000") String corsOrigins,
        @DefaultValue("true") boolean slaEnabled,
        @DefaultValue("0 */15 * * * *") String slaCron,
        @DefaultValue("false") boolean mailEnabled,
        @DefaultValue("noreply@localhost") String mailFrom,
        @DefaultValue("60") int rateLimitCapacity,
        @DefaultValue("60") int rateLimitWindowSeconds
) {
}
