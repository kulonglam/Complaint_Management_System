package com.cms.backend.controller;

import com.cms.backend.dto.InviteUserRequest;
import com.cms.backend.dto.InviteUserResponse;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.UserInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/api/users/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteUserResponse invite(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody InviteUserRequest request
    ) {
        return userInviteService.invite(actor, request);
    }
}
