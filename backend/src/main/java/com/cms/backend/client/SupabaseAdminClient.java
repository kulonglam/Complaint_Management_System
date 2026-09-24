package com.cms.backend.client;

import com.cms.backend.config.SupabaseProperties;
import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SupabaseAdminClient {

    private static final String PLATFORM_ADMIN = "platform_administrator";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SupabaseProperties properties;

    public SupabaseAdminClient(
            RestClient supabaseRestClient,
            ObjectMapper objectMapper,
            SupabaseProperties properties
    ) {
        this.restClient = supabaseRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AuthenticatedUser authenticate(String accessToken) {
        try {
            JsonNode user = call(() -> restClient.get()
                    .uri("/auth/v1/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class));

            if (user == null || user.path("id").isMissingNode()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
            }

            UUID userId = UUID.fromString(user.path("id").asText());
            JsonNode profiles = query("/rest/v1/profiles?id=eq." + userId + "&select=id,email,organization_id");
            JsonNode profile = profiles.isArray() && !profiles.isEmpty()
                    ? profiles.get(0)
                    : objectMapper.createObjectNode();

            String organization = profile.path("organization_id").asText();
            UUID organizationId = organization == null || organization.isBlank() || "null".equals(organization)
                    ? null
                    : UUID.fromString(organization);

            return new AuthenticatedUser(userId, user.path("email").asText(), organizationId);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.BAD_GATEWAY) {
                throw ex;
            }
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
            if (PLATFORM_ADMIN.equals(role.path("key").asText())) {
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
            if (PLATFORM_ADMIN.equals(row.path("role").path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    public JsonNode createAuthUser(String email, String password, String firstName, String lastName) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("email_confirm", true);
        body.put("user_metadata", Map.of("first_name", firstName, "last_name", lastName));

        try {
            JsonNode created = call(() -> restClient.post()
                    .uri("/auth/v1/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));
            if (created == null || created.path("id").isMissingNode()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to invite this user.");
            }
            return created;
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.BAD_GATEWAY) {
                throw ex;
            }
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

        call(() -> restClient.post()
                .uri("/rest/v1/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "resolution=merge-duplicates")
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public UUID findRoleId(UUID organizationId, String roleKey) {
        String encodedKey = encode(roleKey);
        String uri = organizationId == null
                ? "/rest/v1/roles?organization_id=is.null&key=eq." + encodedKey + "&select=id"
                : "/rest/v1/roles?organization_id=eq." + organizationId + "&key=eq." + encodedKey + "&select=id";
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

        call(() -> restClient.post()
                .uri("/rest/v1/user_roles")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "resolution=merge-duplicates")
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public void updateAuthPassword(UUID userId, String password) {
        call(() -> restClient.put()
                .uri("/auth/v1/admin/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", password))
                .retrieve()
                .toBodilessEntity());
    }

    public JsonNode findProfile(UUID userId) {
        JsonNode rows = query("/rest/v1/profiles?id=eq." + userId + "&select=id,email,organization_id,first_name,status");
        if (!rows.isArray() || rows.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        return rows.get(0);
    }

    public JsonNode pendingEmails() {
        return query("/rest/v1/email_outbox?status=eq.PENDING&select=*&order=created_at.asc&limit=25");
    }

    public void markEmail(UUID id, String status, String errorMessage) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        if ("SENT".equals(status)) {
            body.put("sent_at", java.time.OffsetDateTime.now().toString());
        }
        if (errorMessage != null) {
            body.put("error_message", errorMessage);
        }
        call(() -> restClient.patch()
                .uri("/rest/v1/email_outbox?id=eq." + id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public void enqueueEmail(UUID organizationId, String to, String template, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("organization_id", organizationId == null ? null : organizationId.toString());
        body.put("to_email", to);
        body.put("template", template);
        body.put("payload", payload == null ? Map.of() : payload);
        body.put("status", "PENDING");
        call(() -> restClient.post()
                .uri("/rest/v1/email_outbox")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public void assertNamedRateLimit(String bucket, String actor, int limit) {
        call(() -> restClient.post()
                .uri("/rest/v1/rpc/assert_named_rate_limit")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "p_bucket", bucket,
                        "p_actor", actor,
                        "p_limit", limit
                ))
                .retrieve()
                .toBodilessEntity());
    }

    public void assertPlanCapacity(UUID organizationId, String kind) {
        if (organizationId == null) {
            return;
        }
        call(() -> restClient.post()
                .uri("/rest/v1/rpc/assert_plan_capacity")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "p_org", organizationId.toString(),
                        "p_kind", kind
                ))
                .retrieve()
                .toBodilessEntity());
    }

    public int processSlaJobs() {
        JsonNode result = call(() -> restClient.post()
                .uri("/rest/v1/rpc/process_sla_jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class));
        if (result == null || result.isNull() || !result.isNumber()) {
            return 0;
        }
        return result.intValue();
    }

    private JsonNode query(String uri) {
        JsonNode body = call(() -> restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class));
        return body == null ? objectMapper.createArrayNode() : body;
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.configured()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Supabase is not configured. Set SUPABASE_URL in backend/.env to https://YOUR_PROJECT_REF.supabase.co"
            );
        }
        try {
            return request.get();
        } catch (ResourceAccessException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot reach Supabase at " + properties.url() + ". Check SUPABASE_URL."
            );
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Supabase rejected the request.");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
