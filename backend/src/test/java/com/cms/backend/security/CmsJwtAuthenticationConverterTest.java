package com.cms.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.backend.client.SupabaseAdminClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class CmsJwtAuthenticationConverterTest {

    @Test
    void mapsJwtToAuthenticatedUserAuthorities() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "admin@demo.org",
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                false,
                List.of("organization_administrator"),
                List.of("users:create", "users:view")
        );
        SupabaseAdminClient client = mock(SupabaseAdminClient.class);
        when(client.loadAuthenticatedUser(userId, "admin@demo.org")).thenReturn(user);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("email", "admin@demo.org")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        AbstractAuthenticationToken authentication = new CmsJwtAuthenticationConverter(client).convert(jwt);
        assertEquals(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream().anyMatch(item -> "users:create".equals(item.getAuthority())));
        assertTrue(authentication.getAuthorities().stream().anyMatch(item -> "ROLE_USER".equals(item.getAuthority())));
    }
}
