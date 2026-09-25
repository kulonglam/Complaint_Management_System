package com.cms.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InviteUserResponse(
        UUID id,
        String email,
        @JsonProperty("temporary_password") String temporaryPassword,
        @JsonProperty("email_warning") String emailWarning
) {
}
