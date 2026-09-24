package com.cms.backend.controller;

import com.cms.backend.dto.InviteUserRequest;
import com.cms.backend.dto.InviteUserResponse;
import com.cms.backend.dto.ResetAccessRequest;
import com.cms.backend.dto.ResetAccessResponse;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.UserInviteService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserInviteController {

    private final UserInviteService userInviteService;

    public UserInviteController(UserInviteService userInviteService) {
        this.userInviteService = userInviteService;
    }

    @PostMapping({"/api/v1/users", "/api/users/invite"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users:create')")
    public InviteUserResponse invite(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody InviteUserRequest request
    ) {
        return userInviteService.invite(actor, request);
    }

    @PostMapping("/api/v1/users/{userId}/reset-access")
    @PreAuthorize("hasAuthority('users:update')")
    public ResetAccessResponse resetAccess(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID userId
    ) {
        return userInviteService.resetAccess(actor, userId);
    }

    @PostMapping("/api/users/reset-access")
    @PreAuthorize("hasAuthority('users:update')")
    public ResetAccessResponse resetAccessLegacy(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ResetAccessRequest request
    ) {
        return userInviteService.resetAccess(actor, request.userId());
    }
}
