package com.cms.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminMfaFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public AdminMfaFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/me")
                || path.startsWith("/api/jobs")
                || path.startsWith("/api/v1/jobs")
                || path.startsWith("/api/v1/payments/ipn")
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || !user.privilegedAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!(authentication.getCredentials() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("aal2".equalsIgnoreCase(jwt.getClaimAsString("aal"))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "Administrator accounts must complete two-factor authentication.",
                "code", "mfa_required",
                "status", 403
        ));
    }
}
