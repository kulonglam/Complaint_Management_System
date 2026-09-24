package com.cms.backend.controller;

import com.cms.backend.dto.CreateComplaintRequest;
import com.cms.backend.dto.PageResponse;
import com.cms.backend.dto.TransitionComplaintRequest;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.ResourceQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComplaintApiController {

    private final ResourceQueryService resourceQueryService;

    public ComplaintApiController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @GetMapping("/api/v1/complaints")
    @PreAuthorize("hasAnyAuthority('complaints:view', 'complaints:view_all', 'complaints:view_assigned')")
    public PageResponse list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return resourceQueryService.complaints(actor, status, offset, Math.min(limit, 100));
    }

    @GetMapping("/api/v1/complaints/{id}")
    @PreAuthorize("hasAnyAuthority('complaints:view', 'complaints:view_all', 'complaints:view_assigned')")
    public JsonNode one(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return resourceQueryService.complaint(actor, id);
    }

    @PostMapping("/api/v1/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('complaints:create')")
    public JsonNode create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateComplaintRequest request
    ) {
        return resourceQueryService.createComplaint(actor, request);
    }

    @PatchMapping("/api/v1/complaints/{id}")
    @PreAuthorize("hasAnyAuthority('complaints:update', 'complaints:close', 'complaints:reopen', 'complaints:escalate')")
    public JsonNode update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @Valid @RequestBody TransitionComplaintRequest request
    ) {
        return resourceQueryService.transition(actor, id, request);
    }
}
