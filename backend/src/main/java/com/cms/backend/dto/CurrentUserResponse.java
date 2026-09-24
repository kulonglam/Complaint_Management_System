package com.cms.backend.dto;

import com.cms.backend.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        UUID organizationId,
        boolean platformAdmin,
        List<String> roles,
        List<String> permissions
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.id(),
                user.email(),
                user.organizationId(),
                user.platformAdmin(),
                user.roles(),
                user.permissions()
        );
    }
}
