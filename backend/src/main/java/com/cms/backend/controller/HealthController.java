package com.cms.backend.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "ok", true,
                "service", "complaint-management-backend",
                "health", "/health",
                "ready", "/ready",
                "api", "/api/v1",
                "docs", "/swagger-ui.html"
        );
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }
}
