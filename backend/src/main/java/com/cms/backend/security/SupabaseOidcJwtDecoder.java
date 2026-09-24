package com.cms.backend.security;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.SupabaseProperties;
import com.cms.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class SupabaseOidcJwtDecoder implements JwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(SupabaseOidcJwtDecoder.class);

    private final SupabaseProperties properties;
    private final SupabaseAdminClient supabaseAdminClient;
    private final JwtDecoder jwksDecoder;
    private final JwtDecoder hmacDecoder;

    public SupabaseOidcJwtDecoder(SupabaseProperties properties, SupabaseAdminClient supabaseAdminClient) {
        this.properties = properties;
        this.supabaseAdminClient = supabaseAdminClient;
        this.jwksDecoder = createJwksDecoder(properties);
        this.hmacDecoder = createHmacDecoder(properties);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtException last = new JwtException("Unable to validate the access token");
        if (hmacDecoder != null) {
            try {
                return hmacDecoder.decode(token);
            } catch (RuntimeException ex) {
                last = ex instanceof JwtException jwtException ? jwtException : new JwtException(ex.getMessage(), ex);
            }
        }
        if (jwksDecoder != null) {
            try {
                return jwksDecoder.decode(token);
            } catch (RuntimeException ex) {
                last = ex instanceof JwtException jwtException ? jwtException : new JwtException(ex.getMessage(), ex);
            }
        }
        if (properties.configured()) {
            try {
                return introspect(token);
            } catch (RuntimeException ex) {
                throw new JwtException(ex.getMessage(), ex);
            }
        }
        throw last;
    }

    private Jwt introspect(String token) {
        JsonNode user = supabaseAdminClient.authUser(token);
        if (user == null || user.path("id").isMissingNode()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .header("typ", "JWT")
                .subject(user.path("id").asText())
                .claim("email", user.path("email").asText(""))
                .claim("role", "authenticated")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private static JwtDecoder createJwksDecoder(SupabaseProperties properties) {
        String jwkSetUri = properties.jwkSetUri();
        if (jwkSetUri.isBlank()) {
            return null;
        }
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            decoder.setJwtValidator(validators(properties));
            return decoder;
        } catch (RuntimeException ex) {
            log.warn("OIDC JWKS decoder not available: {}", ex.getMessage());
            return null;
        }
    }

    private static JwtDecoder createHmacDecoder(SupabaseProperties properties) {
        if (!properties.hasJwtSecret()) {
            return null;
        }
        SecretKey key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validators(properties));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validators(SupabaseProperties properties) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        String issuer = properties.issuer();
        if (!issuer.isBlank()) {
            OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuer);
            validators.add(jwt -> jwt.getIssuer() == null
                    ? OAuth2TokenValidatorResult.success()
                    : issuerValidator.validate(jwt));
        }
        validators.add(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.equals("authenticated") || role.equals("service_role")) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected token role", null));
        });
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
