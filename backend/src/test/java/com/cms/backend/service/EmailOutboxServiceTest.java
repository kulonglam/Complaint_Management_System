package com.cms.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.SupabaseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock
    private SupabaseAdminClient supabaseAdminClient;

    @Mock
    private EmailService emailService;

    @Test
    void processOutboxContinuesAfterUndeliveredEmail() {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rows = mapper.createArrayNode();
        rows.add(pendingRow(mapper, first, "complaint-received", "one@example.com"));
        rows.add(pendingRow(mapper, second, "user-invited", "two@example.com"));

        when(supabaseAdminClient.pendingEmails()).thenReturn(rows);
        when(emailService.deliver(eq("complaint-received"), eq("one@example.com"), any(Map.class))).thenReturn(false);
        when(emailService.deliver(eq("user-invited"), eq("two@example.com"), any(Map.class))).thenReturn(true);

        EmailOutboxService service = new EmailOutboxService(
                supabaseAdminClient,
                new SupabaseProperties("https://example.supabase.co", "service-role", ""),
                emailService
        );
        service.processOutbox();

        verify(emailService).deliver(eq("complaint-received"), eq("one@example.com"), any(Map.class));
        verify(emailService).deliver(eq("user-invited"), eq("two@example.com"), any(Map.class));
        verify(supabaseAdminClient).markEmail(eq(first), eq("PENDING"), eq("Waiting for SMTP or RESEND_API_KEY"));
        verify(supabaseAdminClient).markEmail(eq(second), eq("SENT"), isNull());
        verify(supabaseAdminClient, times(2)).markEmail(any(UUID.class), any(), any());
    }

    private static ObjectNode pendingRow(ObjectMapper mapper, UUID id, String template, String to) {
        ObjectNode row = mapper.createObjectNode();
        row.put("id", id.toString());
        row.put("template", template);
        row.put("to_email", to);
        row.set("payload", mapper.createObjectNode());
        return row;
    }
}
