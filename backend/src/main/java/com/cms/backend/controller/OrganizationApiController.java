package com.cms.backend.controller;

import com.cms.backend.dto.PageResponse;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.ResourceQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationApiController {

    private final ResourceQueryService resourceQueryService;

    public OrganizationApiController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @GetMapping("/api/v1/organizations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PageResponse list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return resourceQueryService.organizations(actor, offset, Math.min(limit, 100));
    }
}
