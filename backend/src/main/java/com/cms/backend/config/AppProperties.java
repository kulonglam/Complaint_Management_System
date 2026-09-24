package com.cms.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jobSecret = "change-me";
    private String corsOrigins = "http://localhost:3000";

    public String getJobSecret() {
        return jobSecret;
    }

    public void setJobSecret(String jobSecret) {
        this.jobSecret = jobSecret;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
}
