package com.cms.backend.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InviteUserResponseJsonTest {

    @Test
    void springSnakeCaseMapperWritesSnakeCaseKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        String json = mapper.writeValueAsString(sample());

        assertTrue(json.contains("\"temporary_password\""));
        assertTrue(json.contains("\"email_warning\""));
        assertFalse(json.contains("temporaryPassword"));
        assertFalse(json.contains("emailWarning"));
    }

    @Test
    void defaultMapperHonorsJsonPropertyOnSerialize() throws Exception {
        String json = new ObjectMapper().writeValueAsString(sample());

        assertTrue(json.contains("\"temporary_password\""));
        assertTrue(json.contains("\"email_warning\""));
        assertFalse(json.contains("temporaryPassword"));
        assertFalse(json.contains("emailWarning"));
    }

    private static InviteUserResponse sample() {
        return new InviteUserResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "staff@example.com",
                "Tmp-secret",
                "Resend is in test mode."
        );
    }
}
