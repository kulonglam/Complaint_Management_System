package com.cms.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminMfaFilterTest {

    private final AdminMfaFilter filter = new AdminMfaFilter(new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsPrivilegedAdminWithoutAal2() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                "admin@demo.org",
                UUID.randomUUID(),
                false,
                List.of("organization_administrator"),
                List.of("settings:view")
        );
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("aal", "aal1")
                .subject(user.id().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, jwt, user.authorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setServletPath("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals(true, response.getContentAsString().contains("mfa_required"));
    }

    @Test
    void allowsPrivilegedAdminWithAal2() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                "admin@demo.org",
                UUID.randomUUID(),
                true,
                List.of("platform_administrator"),
                List.of()
        );
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("aal", "aal2")
                .subject(user.id().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, jwt, user.authorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setServletPath("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }
}
