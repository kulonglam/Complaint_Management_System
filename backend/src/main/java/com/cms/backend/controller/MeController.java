package com.cms.backend.controller;

import com.cms.backend.dto.CurrentUserResponse;
import com.cms.backend.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/v1/me")
    @PreAuthorize("isAuthenticated()")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.from(user);
    }
}
