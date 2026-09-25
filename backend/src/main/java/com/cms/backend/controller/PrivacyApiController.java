package com.cms.backend.controller;

import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.ResourceQueryService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyApiController {

    private final ResourceQueryService resourceQueryService;

    public PrivacyApiController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @PostMapping("/api/v1/privacy/erasure")
    @PreAuthorize("hasAuthority('settings:update')")
    public Map<String, Integer> erase(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody Map<String, String> body
    ) {
        return Map.of("redacted", resourceQueryService.requestErasure(actor, body.get("email")));
    }
}
