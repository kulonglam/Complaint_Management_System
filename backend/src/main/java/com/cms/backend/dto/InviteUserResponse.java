package com.cms.backend.dto;

import java.util.UUID;

public record InviteUserResponse(UUID id, String email, String temporaryPassword, String emailWarning) {
}
