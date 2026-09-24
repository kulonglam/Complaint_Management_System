package com.cms.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.SupabaseProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseOidcJwtDecoderTest {

    @Test
    void decodesHs256SupabaseAccessToken() throws Exception {
        String secret = "test-hs256-secret-which-is-long-enough-32b";
        UUID subject = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .claim("email", "officer@demo.org")
                .claim("role", "authenticated")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

        SupabaseOidcJwtDecoder decoder = new SupabaseOidcJwtDecoder(
                new SupabaseProperties("", "", secret),
                null
        );
        Jwt jwt = decoder.decode(signed.serialize());
        assertEquals(subject.toString(), jwt.getSubject());
        assertEquals("officer@demo.org", jwt.getClaimAsString("email"));
    }
}
