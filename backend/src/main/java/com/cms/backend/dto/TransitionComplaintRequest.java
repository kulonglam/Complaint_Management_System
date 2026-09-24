package com.cms.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransitionComplaintRequest(
        @NotBlank @Size(max = 40) String status,
        @Size(max = 500) String reason
) {
}
