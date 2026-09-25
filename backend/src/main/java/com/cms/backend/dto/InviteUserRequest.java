package com.cms.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @Email(message = "Enter a valid email address.")
        @NotBlank(message = "Enter an email address.")
        @Size(max = 254)
        String email,
        @JsonProperty("first_name")
        @JsonAlias("firstName")
        @NotBlank(message = "Enter a first name.")
        @Size(max = 80)
        String firstName,
        @JsonProperty("last_name")
        @JsonAlias("lastName")
        @NotBlank(message = "Enter a last name.")
        @Size(max = 80)
        String lastName,
        @JsonProperty("role_key")
        @JsonAlias("roleKey")
        @NotBlank(message = "Choose a role.")
        @Size(max = 64)
        String roleKey,
        @Size(max = 128)
        String password
) {
}
