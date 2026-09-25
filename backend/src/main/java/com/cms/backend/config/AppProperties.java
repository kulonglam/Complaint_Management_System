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
        @DefaultValue("60") int rateLimitWindowSeconds,
        @DefaultValue("") String resendApiKey,
        @DefaultValue("") String stripeSecretKey,
        @DefaultValue("true") boolean retentionEnabled,
        @DefaultValue("0 30 2 * * *") String retentionCron,
        @DefaultValue("UGX") String paymentCurrency,
        @DefaultValue("false") boolean paymentDemo,
        @DefaultValue("http://localhost:8000") String publicApiUrl,
        @DefaultValue("") String pesapalConsumerKey,
        @DefaultValue("") String pesapalConsumerSecret,
        @DefaultValue("") String pesapalIpnId,
        @DefaultValue("sandbox") String pesapalEnvironment,
        @DefaultValue("") String mtnSubscriptionKey,
        @DefaultValue("") String mtnApiUser,
        @DefaultValue("") String mtnApiKey,
        @DefaultValue("sandbox") String mtnTargetEnvironment,
        @DefaultValue("") String airtelClientId,
        @DefaultValue("") String airtelClientSecret,
        @DefaultValue("sandbox") String airtelEnvironment
) {
    public String frontendOrigin() {
        String first = corsOrigins == null ? "" : corsOrigins.split(",")[0].trim();
        return first.isBlank() ? "http://localhost:3000" : first;
    }

    public boolean pesapalConfigured() {
        return notBlank(pesapalConsumerKey) && notBlank(pesapalConsumerSecret);
    }

    public boolean mtnConfigured() {
        return notBlank(mtnSubscriptionKey) && notBlank(mtnApiUser) && notBlank(mtnApiKey);
    }

    public boolean airtelConfigured() {
        return notBlank(airtelClientId) && notBlank(airtelClientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
