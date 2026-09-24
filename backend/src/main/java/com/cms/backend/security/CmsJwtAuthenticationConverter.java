package com.cms.backend.security;

import com.cms.backend.client.SupabaseAdminClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CmsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SupabaseAdminClient supabaseAdminClient;

    public CmsJwtAuthenticationConverter(SupabaseAdminClient supabaseAdminClient) {
        this.supabaseAdminClient = supabaseAdminClient;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        AuthenticatedUser user = supabaseAdminClient.loadAuthenticatedUser(userId, emailFrom(jwt));
        return new UsernamePasswordAuthenticationToken(user, jwt, user.authorities());
    }

    static String emailFrom(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof Map<?, ?> map && map.get("email") != null) {
            return String.valueOf(map.get("email"));
        }
        return "";
    }
}
