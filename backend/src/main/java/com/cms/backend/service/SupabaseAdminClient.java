package com.cms.backend.service;

import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SupabaseAdminClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SupabaseAdminClient(RestClient supabaseRestClient, ObjectMapper objectMapper) {
        this.restClient = supabaseRestClient;
        this.objectMapper = objectMapper;
    }

    public AuthenticatedUser authenticate(String accessToken) {
        try {
            JsonNode user = restClient.get()
                    .uri("/auth/v1/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (user == null || user.path("id").isMissingNode()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
            }

            UUID userId = UUID.fromString(user.path("id").asText());
            JsonNode profiles = query("/rest/v1/profiles?id=eq." + userId + "&select=id,email,organization_id");
            JsonNode profile = profiles.isArray() && !profiles.isEmpty() ? profiles.get(0) : objectMapper.createObjectNode();

            UUID organizationId = profile.path("organization_id").isNull() || profile.path("organization_id").asText().isBlank()
                    ? null
                    : UUID.fromString(profile.path("organization_id").asText());

            return new AuthenticatedUser(userId, user.path("email").asText(), organizationId);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
    }

    public boolean hasPermission(UUID userId, String permission) {
        String select = "role:roles(key,role_permissions(permission:permissions(key)))";
        JsonNode rows = query("/rest/v1/user_roles?user_id=eq." + userId + "&select=" + select);

        if (!rows.isArray()) {
            return false;
        }

        for (JsonNode row : rows) {
            JsonNode role = row.path("role");
            if ("platform_administrator".equals(role.path("key").asText())) {
                return true;
            }
            for (JsonNode mapping : role.path("role_permissions")) {
                if (permission.equals(mapping.path("permission").path("key").asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPlatformAdmin(UUID userId) {
        JsonNode rows = query("/rest/v1/user_roles?user_id=eq." + userId + "&select=role:roles(key)");
        if (!rows.isArray()) {
            return false;
        }
        for (JsonNode row : rows) {
            if ("platform_administrator".equals(row.path("role").path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    public JsonNode createAuthUser(String email, String password, String firstName, String lastName) {
        Map<String, Object> metadata = Map.of("first_name", firstName, "last_name", lastName);
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("email_confirm", true);
        body.put("user_metadata", metadata);

        try {
            return restClient.post()
                    .uri("/auth/v1/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to invite this user.");
        }
    }

    public void upsertProfile(UUID id, UUID organizationId, String firstName, String lastName, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("organization_id", organizationId == null ? null : organizationId.toString());
        body.put("first_name", firstName);
        body.put("last_name", lastName);
        body.put("email", email);
        body.put("status", "ACTIVE");

        restClient.post()
                .uri("/rest/v1/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "resolution=merge-duplicates")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public UUID findRoleId(UUID organizationId, String roleKey) {
        String uri = organizationId == null
                ? "/rest/v1/roles?organization_id=is.null&key=eq." + roleKey + "&select=id"
                : "/rest/v1/roles?organization_id=eq." + organizationId + "&key=eq." + roleKey + "&select=id";
        JsonNode rows = query(uri);
        if (!rows.isArray() || rows.isEmpty()) {
            return null;
        }
        return UUID.fromString(rows.get(0).path("id").asText());
    }

    public void upsertUserRole(UUID userId, UUID roleId, UUID organizationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId.toString());
        body.put("role_id", roleId.toString());
        body.put("organization_id", organizationId == null ? null : organizationId.toString());

        restClient.post()
                .uri("/rest/v1/user_roles")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "resolution=merge-duplicates")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public Integer processSlaJobs() {
        JsonNode result = restClient.post()
                .uri("/rest/v1/rpc/process_sla_jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
        if (result == null || result.isNull()) {
            return 0;
        }
        return result.isNumber() ? result.intValue() : 0;
    }

    private JsonNode query(String uri) {
        JsonNode body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
        return body == null ? objectMapper.createArrayNode() : body;
    }
}
