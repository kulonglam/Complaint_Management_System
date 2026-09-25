package com.cms.backend.client;

import com.cms.backend.config.SupabaseProperties;
import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public JsonNode authUser(String accessToken) {
        return call(() -> restClient.get()
                .uri("/auth/v1/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class));
    }

    public AuthenticatedUser loadAuthenticatedUser(UUID userId, String email) {
        JsonNode profile = findProfile(userId);
        UUID organizationId = uuidOrNull(profile.path("organization_id").asText());
        String resolvedEmail = email == null || email.isBlank() ? profile.path("email").asText("") : email;

        List<String> roles = new ArrayList<>();
        Set<String> permissions = new LinkedHashSet<>();
        boolean platformAdmin = false;
        JsonNode rows = query("/rest/v1/user_roles?user_id=eq." + userId
                + "&select=role:roles(key,role_permissions(permission:permissions(key)))");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode role = row.path("role");
                String key = role.path("key").asText("");
                if (!key.isBlank()) {
                    roles.add(key);
                }
                if (PLATFORM_ADMIN.equals(key)) {
                    platformAdmin = true;
                }
                for (JsonNode mapping : role.path("role_permissions")) {
                    String permission = mapping.path("permission").path("key").asText("");
                    if (!permission.isBlank()) {
                        permissions.add(permission);
                    }
                }
            }
        }
        if (platformAdmin) {
            JsonNode all = query("/rest/v1/permissions?select=key");
            if (all.isArray()) {
                for (JsonNode item : all) {
                    String key = item.path("key").asText("");
                    if (!key.isBlank()) {
                        permissions.add(key);
                    }
                }
            }
        }
        return new AuthenticatedUser(
                userId,
                resolvedEmail,
                organizationId,
                platformAdmin,
                List.copyOf(roles),
                List.copyOf(permissions)
        );
    }

    public QueryPage list(String path, int offset, int limit) {
        var entity = call(() -> restClient.get()
                .uri(path)
                .header("Prefer", "count=exact")
                .header("Range-Unit", "items")
                .header("Range", offset + "-" + (offset + Math.max(limit, 1) - 1))
                .retrieve()
                .toEntity(JsonNode.class));
        JsonNode body = entity.getBody() == null ? objectMapper.createArrayNode() : entity.getBody();
        long total = parseTotal(entity.getHeaders().getFirst("Content-Range"), body.size());
        return new QueryPage(body, total, offset, limit);
    }

    public JsonNode getById(String table, UUID id, String select) {
        JsonNode rows = query("/rest/v1/" + table + "?id=eq." + id + "&select=" + select);
        if (!rows.isArray() || rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found");
        }
        return rows.get(0);
    }

    public JsonNode userPost(String accessToken, String path, Object body) {
        return call(() -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(JsonNode.class));
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
        JsonNode rows = call(() -> restClient.post()
                .uri("/rest/v1/rpc/list_pending_emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("p_limit", 25))
                .retrieve()
                .body(JsonNode.class));
        return rows == null ? objectMapper.createArrayNode() : rows;
    }

    public void markEmail(UUID id, String status, String errorMessage) {
        markEmail(id, status, errorMessage, null);
    }

    public void markEmail(UUID id, String status, String errorMessage, Integer attemptCount) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        if ("SENT".equals(status)) {
            body.put("sent_at", java.time.OffsetDateTime.now().toString());
        }
        if (errorMessage != null) {
            body.put("error_message", errorMessage);
        }
        if (attemptCount != null) {
            body.put("attempt_count", attemptCount);
            if ("PENDING".equals(status)) {
                int minutes = (int) Math.min(60, Math.pow(2, Math.min(attemptCount, 6)));
                body.put("next_attempt_at", java.time.OffsetDateTime.now().plusMinutes(minutes).toString());
            }
        }
        call(() -> restClient.patch()
                .uri("/rest/v1/email_outbox?id=eq." + id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public int purgeExpiredRecords() {
        JsonNode result = call(() -> restClient.post()
                .uri("/rest/v1/rpc/purge_expired_records")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class));
        if (result == null || result.isNull() || !result.isNumber()) {
            return 0;
        }
        return result.intValue();
    }

    public JsonNode exportAuditLogs(UUID organizationId, String from, String to, String accessToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_from", from);
        body.put("p_to", to);
        if (accessToken != null && !accessToken.isBlank()) {
            return userPost(accessToken, "/rest/v1/rpc/export_audit_logs", body);
        }
        StringBuilder path = new StringBuilder("/rest/v1/audit_logs?select=id,created_at,action,entity_type,entity_id,user_id,old_values,new_values,metadata&order=created_at.desc");
        if (organizationId != null) {
            path.append("&organization_id=eq.").append(organizationId);
        }
        if (from != null && !from.isBlank()) {
            path.append("&created_at=gte.").append(encode(from));
        }
        if (to != null && !to.isBlank()) {
            path.append("&created_at=lte.").append(encode(to));
        }
        return query(path + "&limit=10000");
    }

    public int requestErasure(String email, String accessToken) {
        JsonNode result = userPost(accessToken, "/rest/v1/rpc/request_subject_erasure", Map.of("p_email", email));
        if (result == null || result.isNull() || !result.isNumber()) {
            return 0;
        }
        return result.intValue();
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

    public static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static long parseTotal(String contentRange, int fallback) {
        if (contentRange == null || !contentRange.contains("/")) {
            return fallback;
        }
        try {
            return Long.parseLong(contentRange.substring(contentRange.indexOf('/') + 1));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record QueryPage(JsonNode data, long total, int offset, int limit) {
    }
}
