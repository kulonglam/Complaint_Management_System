package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.client.SupabaseAdminClient.QueryPage;
import com.cms.backend.dto.CreateComplaintRequest;
import com.cms.backend.dto.PageResponse;
import com.cms.backend.dto.TransitionComplaintRequest;
import com.cms.backend.exception.ApiException;
import com.cms.backend.security.AuthTokens;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResourceQueryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SupabaseAdminClient supabaseAdminClient;

    public ResourceQueryService(SupabaseAdminClient supabaseAdminClient) {
        this.supabaseAdminClient = supabaseAdminClient;
    }

    public PageResponse users(AuthenticatedUser actor, int offset, int limit) {
        requireOrg(actor);
        String filter = actor.platformAdmin()
                ? "/rest/v1/profiles?select=id,email,first_name,last_name,status,organization_id,job_title,created_at&order=created_at.desc"
                : "/rest/v1/profiles?organization_id=eq." + actor.organizationId()
                        + "&select=id,email,first_name,last_name,status,organization_id,job_title,created_at&order=created_at.desc";
        return page(supabaseAdminClient.list(filter, offset, limit));
    }

    public JsonNode user(AuthenticatedUser actor, UUID id) {
        JsonNode profile = supabaseAdminClient.getById(
                "profiles",
                id,
                "id,email,first_name,last_name,status,organization_id,job_title,department_id,created_at"
        );
        assertTenant(actor, profile.path("organization_id").asText());
        return profile;
    }

    public PageResponse complaints(AuthenticatedUser actor, String status, int offset, int limit) {
        requireOrg(actor);
        StringBuilder path = new StringBuilder("/rest/v1/complaints?select=id,reference_number,title,status,priority,organization_id,department_id,assigned_to,submitted_at,due_date&order=submitted_at.desc");
        if (!actor.platformAdmin()) {
            path.append("&organization_id=eq.").append(actor.organizationId());
        }
        if (status != null && !status.isBlank()) {
            path.append("&status=eq.").append(status);
        }
        return page(supabaseAdminClient.list(path.toString(), offset, limit));
    }

    public JsonNode complaint(AuthenticatedUser actor, UUID id) {
        JsonNode complaint = supabaseAdminClient.getById(
                "complaints",
                id,
                "id,reference_number,title,description,status,priority,organization_id,department_id,category_id,assigned_to,complainant_name,complainant_email,submitted_at,due_date,updated_at"
        );
        assertTenant(actor, complaint.path("organization_id").asText());
        return complaint;
    }

    public JsonNode createComplaint(AuthenticatedUser actor, CreateComplaintRequest request) {
        requireOrg(actor);
        if (actor.organizationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You are not assigned to an organization");
        }
        String token = AuthTokens.bearer();
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organization_id", actor.organizationId().toString());
        body.put("title", request.title());
        body.put("description", request.description());
        body.put("priority", request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority());
        body.put("status", "SUBMITTED");
        body.put("reference_number", "API-" + HexFormat.of().formatHex(randomBytes(5)).toUpperCase());
        body.put("tracking_code_hash", sha256(UUID.randomUUID().toString()));
        body.put("category_id", request.categoryId());
        body.put("department_id", request.departmentId());
        body.put("complainant_name", request.complainantName());
        body.put("complainant_email", request.complainantEmail());
        JsonNode created = supabaseAdminClient.userPost(token, "/rest/v1/complaints", body);
        if (created != null && created.isArray() && !created.isEmpty()) {
            return created.get(0);
        }
        return created;
    }

    public JsonNode transition(AuthenticatedUser actor, UUID id, TransitionComplaintRequest request) {
        complaint(actor, id);
        String token = AuthTokens.bearer();
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return supabaseAdminClient.userPost(token, "/rest/v1/rpc/transition_complaint_status", Map.of(
                "p_complaint_id", id.toString(),
                "p_new_status", request.status(),
                "p_reason", request.reason() == null ? "" : request.reason()
        ));
    }

    public PageResponse organizations(AuthenticatedUser actor, int offset, int limit) {
        if (!actor.platformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return page(supabaseAdminClient.list(
                "/rest/v1/organizations?select=id,name,slug,status,created_at&order=created_at.desc",
                offset,
                limit
        ));
    }

    public PageResponse auditLogs(AuthenticatedUser actor, String from, String to, int offset, int limit) {
        requireOrg(actor);
        StringBuilder path = new StringBuilder(
                "/rest/v1/audit_logs?select=id,created_at,action,entity_type,entity_id,user_id,old_values,new_values,metadata,organization_id&order=created_at.desc"
        );
        if (!actor.platformAdmin()) {
            path.append("&organization_id=eq.").append(actor.organizationId());
        }
        if (from != null && !from.isBlank()) {
            path.append("&created_at=gte.").append(from);
        }
        if (to != null && !to.isBlank()) {
            path.append("&created_at=lte.").append(to);
        }
        return page(supabaseAdminClient.list(path.toString(), offset, limit));
    }

    public JsonNode exportAuditLogs(AuthenticatedUser actor, String from, String to) {
        requireOrg(actor);
        String token = AuthTokens.bearer();
        return supabaseAdminClient.exportAuditLogs(actor.organizationId(), from, to, token);
    }

    public int requestErasure(AuthenticatedUser actor, String email) {
        requireOrg(actor);
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email is required", "validation");
        }
        String token = AuthTokens.bearer();
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return supabaseAdminClient.requestErasure(email, token);
    }

    public PageResponse emails(AuthenticatedUser actor, int offset, int limit) {
        requireOrg(actor);
        String filter = actor.platformAdmin()
                ? "/rest/v1/email_outbox?select=id,to_email,template,status,error_message,created_at,sent_at,organization_id&order=created_at.desc"
                : "/rest/v1/email_outbox?organization_id=eq." + actor.organizationId()
                        + "&select=id,to_email,template,status,error_message,created_at,sent_at,organization_id&order=created_at.desc";
        return page(supabaseAdminClient.list(filter, offset, limit));
    }

    private static PageResponse page(QueryPage queryPage) {
        return new PageResponse(queryPage.data(), new PageResponse.Page(queryPage.offset(), queryPage.limit(), queryPage.total()));
    }

    private static void requireOrg(AuthenticatedUser actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!actor.platformAdmin() && actor.organizationId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not assigned to an organization");
        }
    }

    private static void assertTenant(AuthenticatedUser actor, String organizationId) {
        if (actor.platformAdmin()) {
            return;
        }
        if (actor.organizationId() == null || !actor.organizationId().toString().equals(organizationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found");
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
