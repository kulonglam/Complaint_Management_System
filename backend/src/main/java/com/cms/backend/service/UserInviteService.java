package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.dto.InviteUserRequest;
import com.cms.backend.dto.InviteUserResponse;
import com.cms.backend.dto.ResetAccessRequest;
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

        boolean platformAdmin = supabaseAdminClient.isPlatformAdmin(actor.id());
        if (!platformAdmin && !supabaseAdminClient.hasPermission(actor.id(), Permissions.USERS_CREATE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to create users");
        }
        if (actor.organizationId() == null && !platformAdmin) {
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

        emailService.send("user-invited", request.email(), Map.of(
                "first_name", request.firstName(),
                "organization", organizationId == null ? "" : organizationId.toString()
        ), organizationId);

        return new InviteUserResponse(userId, request.email());
    }

    public ResetAccessResponse resetAccess(AuthenticatedUser actor, ResetAccessRequest request) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean platformAdmin = supabaseAdminClient.isPlatformAdmin(actor.id());
        if (!platformAdmin && !supabaseAdminClient.hasPermission(actor.id(), Permissions.USERS_UPDATE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to reset access");
        }

        JsonNode profile = supabaseAdminClient.findProfile(request.userId());
        if (profile.path("id").isMissingNode()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        String organization = profile.path("organization_id").asText();
        if (!platformAdmin && (actor.organizationId() == null
                || !actor.organizationId().toString().equals(organization))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only reset users in your organization");
        }

        String password = temporaryPassword();
        supabaseAdminClient.updateAuthPassword(request.userId(), password);
        UUID organizationId = organization == null || organization.isBlank() || "null".equals(organization)
                ? null
                : UUID.fromString(organization);
        emailService.send("user-invited", profile.path("email").asText(), Map.of(
                "first_name", profile.path("first_name").asText(""),
                "temporary_password", password
        ), organizationId);
        return new ResetAccessResponse(request.userId().toString(), password);
    }

    private static String temporaryPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return "Tmp-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "!9";
    }
}
