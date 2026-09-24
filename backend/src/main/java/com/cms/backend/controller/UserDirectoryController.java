package com.cms.backend.controller;

import com.cms.backend.dto.PageResponse;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.ResourceQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserDirectoryController {

    private final ResourceQueryService resourceQueryService;

    public UserDirectoryController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @GetMapping("/api/v1/users")
    @PreAuthorize("hasAuthority('users:view')")
    public PageResponse list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return resourceQueryService.users(actor, offset, Math.min(limit, 100));
    }

    @GetMapping("/api/v1/users/{id}")
    @PreAuthorize("hasAuthority('users:view')")
    public JsonNode one(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return resourceQueryService.user(actor, id);
    }
}
