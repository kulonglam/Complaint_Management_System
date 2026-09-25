package com.cms.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiCatalogController {

    @GetMapping("/api/v1")
    public Map<String, Object> catalog() {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("me", "/api/v1/me");
        resources.put("users", "/api/v1/users");
        resources.put("complaints", "/api/v1/complaints");
        resources.put("organizations", "/api/v1/organizations");
        resources.put("emails", "/api/v1/emails");
        resources.put("auditLogs", "/api/v1/audit-logs");
        resources.put("privacy", "/api/v1/privacy/erasure");
        resources.put("jobs", "/api/v1/jobs/sla");
        return Map.of(
                "name", "complaint-management-api",
                "version", "v1",
                "auth", "OIDC Bearer JWT from Supabase Auth",
                "docs", "/swagger-ui.html",
                "resources", resources
        );
    }
}
