package com.cms.backend.controller;

import com.cms.backend.dto.PageResponse;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.ResourceQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditApiController {

    private final ResourceQueryService resourceQueryService;

    public AuditApiController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @GetMapping("/api/v1/audit-logs")
    @PreAuthorize("hasAuthority('audit_logs:view')")
    public PageResponse list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return resourceQueryService.auditLogs(actor, from, to, offset, Math.min(limit, 200));
    }

    @GetMapping("/api/v1/audit-logs/export")
    @PreAuthorize("hasAnyAuthority('audit_logs:view', 'reports:export')")
    public JsonNode export(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return resourceQueryService.exportAuditLogs(actor, from, to);
    }
}
