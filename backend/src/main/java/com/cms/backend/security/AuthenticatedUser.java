package com.cms.backend.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, UUID organizationId) {
}
