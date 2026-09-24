package com.cms.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Size(max = 64) String roleKey,
        @Size(max = 128) String password
) {
}
