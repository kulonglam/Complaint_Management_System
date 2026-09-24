package com.cms.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthTokens {

    private AuthTokens() {
    }

    public static String bearer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        return null;
    }
}
