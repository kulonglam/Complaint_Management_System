package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.SupabaseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final SupabaseAdminClient supabaseAdminClient;
    private final SupabaseProperties supabaseProperties;
    private final EmailService emailService;

    public EmailOutboxService(
            SupabaseAdminClient supabaseAdminClient,
            SupabaseProperties supabaseProperties,
            EmailService emailService
    ) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.supabaseProperties = supabaseProperties;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelay = 60000)
    public void processOutbox() {
        if (!supabaseProperties.configured()) {
            return;
        }
        try {
            JsonNode rows = supabaseAdminClient.pendingEmails();
            if (rows == null || !rows.isArray()) {
                return;
            }
            for (JsonNode row : rows) {
                UUID id = UUID.fromString(row.path("id").asText());
                try {
                    boolean delivered = emailService.deliver(
                            row.path("template").asText(),
                            row.path("to_email").asText(),
                            toMap(row.path("payload"))
                    );
                    supabaseAdminClient.markEmail(id, delivered ? "SENT" : "PENDING", delivered ? null : "Waiting for SMTP or RESEND_API_KEY");
                    if (!delivered) {
                        return;
                    }
                } catch (Exception ex) {
                    supabaseAdminClient.markEmail(id, "FAILED", ex.getMessage());
                    log.warn("email outbox failed id={}: {}", id, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("email outbox skipped: {}", ex.getMessage());
        }
    }

    private static Map<String, Object> toMap(JsonNode payload) {
        Map<String, Object> data = new HashMap<>();
        if (payload == null || !payload.isObject()) {
            return data;
        }
        payload.fields().forEachRemaining(entry -> data.put(entry.getKey(), entry.getValue().asText()));
        return data;
    }
}
