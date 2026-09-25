package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.dto.InviteUserRequest;
import com.cms.backend.dto.InviteUserResponse;
import com.cms.backend.dto.ResetAccessResponse;
import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.SecureRandom;
import java.util.Base64;
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
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (actor.organizationId() == null && !actor.platformAdmin()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You are not assigned to an organization");
        }

        UUID organizationId = actor.organizationId();
        supabaseAdminClient.assertPlanCapacity(organizationId, "profiles");
        String password = request.password() == null || request.password().isBlank()
                ? temporaryPassword()
                : request.password();

        JsonNode created = supabaseAdminClient.createAuthUser(
                request.email(),
                password,
                request.firstName(),
                request.lastName()
        );
        UUID userId = UUID.fromString(created.path("id").asText());

        supabaseAdminClient.upsertProfile(
                userId,
                organizationId,
                request.firstName(),
                request.lastName(),
                request.email()
        );

        UUID roleId = supabaseAdminClient.findRoleId(organizationId, request.roleKey());
        if (roleId != null) {
            supabaseAdminClient.upsertUserRole(userId, roleId, organizationId);
        }

        EmailService.SendResult email = emailService.send("user-invited", request.email(), Map.of(
                "first_name", request.firstName(),
                "organization", organizationId == null ? "" : organizationId.toString(),
                "temporary_password", password
        ), organizationId);

        return new InviteUserResponse(userId, request.email(), password, email.warning());
    }

    public ResetAccessResponse resetAccess(AuthenticatedUser actor, UUID userId) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        JsonNode profile = supabaseAdminClient.findProfile(userId);
        if (profile.path("id").isMissingNode()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        String organization = profile.path("organization_id").asText();
        if (!actor.platformAdmin() && (actor.organizationId() == null
                || !actor.organizationId().toString().equals(organization))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only reset users in your organization");
        }

        String password = temporaryPassword();
        supabaseAdminClient.updateAuthPassword(userId, password);
        UUID organizationId = organization == null || organization.isBlank() || "null".equals(organization)
                ? null
                : UUID.fromString(organization);
        emailService.send("user-invited", profile.path("email").asText(), Map.of(
                "first_name", profile.path("first_name").asText(""),
                "temporary_password", password
        ), organizationId);
        return new ResetAccessResponse(userId.toString(), password);
    }

    private static String temporaryPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return "Tmp-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "!9";
    }
}
