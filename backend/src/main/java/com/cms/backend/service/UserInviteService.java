package com.cms.backend.service;

import com.cms.backend.dto.InviteUserRequest;
import com.cms.backend.dto.InviteUserResponse;
import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SupabaseAdminClient supabaseAdminClient;
    private final EmailService emailService;

    public UserInviteService(SupabaseAdminClient supabaseAdminClient, EmailService emailService) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.emailService = emailService;
    }

    public InviteUserResponse invite(AuthenticatedUser actor, InviteUserRequest request) {
        boolean platformAdmin = supabaseAdminClient.isPlatformAdmin(actor.id());
        if (!platformAdmin && !supabaseAdminClient.hasPermission(actor.id(), "users:create")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to create users");
        }
        if (actor.organizationId() == null && !platformAdmin) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You are not assigned to an organization");
        }

        UUID organizationId = actor.organizationId();
        String password = request.getPassword() == null || request.getPassword().isBlank()
                ? "Tmp-" + Long.toString(Math.abs(RANDOM.nextLong()), 36) + "!9"
                : request.getPassword();

        JsonNode created = supabaseAdminClient.createAuthUser(
                request.getEmail(),
                password,
                request.getFirstName(),
                request.getLastName()
        );
        UUID userId = UUID.fromString(created.path("id").asText());

        supabaseAdminClient.upsertProfile(
                userId,
                organizationId,
                request.getFirstName(),
                request.getLastName(),
                request.getEmail()
        );

        UUID roleId = supabaseAdminClient.findRoleId(organizationId, request.getRoleKey());
        if (roleId != null) {
            supabaseAdminClient.upsertUserRole(userId, roleId, organizationId);
        }

        emailService.send("user-invited", request.getEmail(), Map.of(
                "first_name", request.getFirstName(),
                "organization", organizationId
        ));

        return new InviteUserResponse(userId, request.getEmail());
    }
}
