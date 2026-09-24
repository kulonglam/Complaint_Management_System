package com.cms.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResetAccessRequest(@NotNull UUID userId) {
}
