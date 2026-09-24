package com.cms.backend.config;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.security.SupabaseOidcJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
public class OidcJwtConfig {

    @Bean
    JwtDecoder jwtDecoder(SupabaseProperties properties, SupabaseAdminClient supabaseAdminClient) {
        return new SupabaseOidcJwtDecoder(properties, supabaseAdminClient);
    }
}
