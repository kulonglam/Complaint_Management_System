package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    static final Map<String, String> SUBJECTS = Map.of(
            "complaint-received", "Your complaint has been received",
            "complaint-assigned", "A complaint was assigned",
            "complaint-status-updated", "A complaint status changed",
            "complaint-resolved", "A complaint was resolved",
            "complaint-closed", "A complaint was closed",
            "complaint-reopened", "A complaint was reopened",
            "sla-warning", "A complaint is approaching its SLA",
            "complaint-overdue", "A complaint is overdue",
            "user-invited", "You have been invited to the complaint platform"
    );

    static final Map<String, String> BODIES = Map.of(
            "complaint-received", "Your complaint {reference_number} was received by {organization}. Keep your tracking code safe.",
            "complaint-assigned", "Complaint {reference_number} was assigned.",
            "complaint-status-updated", "Complaint {reference_number} is now {status}.",
            "complaint-resolved", "Complaint {reference_number} has been resolved.",
            "complaint-closed", "Complaint {reference_number} has been closed.",
            "complaint-reopened", "Complaint {reference_number} has been reopened.",
            "sla-warning", "Complaint {reference_number} is approaching its due date.",
            "complaint-overdue", "Complaint {reference_number} is overdue.",
            "user-invited", "Hello {first_name}, you have been invited to the complaint platform. Sign in at {login_url} with this temporary password: {temporary_password}"
    );

    private final AppProperties appProperties;
    private final SupabaseProperties supabaseProperties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<SupabaseAdminClient> supabaseAdminClient;

    public EmailService(
            AppProperties appProperties,
            SupabaseProperties supabaseProperties,
            ObjectProvider<JavaMailSender> mailSender,
            ObjectProvider<SupabaseAdminClient> supabaseAdminClient
    ) {
        this.appProperties = appProperties;
        this.supabaseProperties = supabaseProperties;
        this.mailSender = mailSender;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    public void send(String template, String to, Map<String, Object> data) {
        send(template, to, data, null);
    }

    public SendResult send(String template, String to, Map<String, Object> data, UUID organizationId) {
        Map<String, Object> payload = withInviteDefaults(template, data);
        String subject = SUBJECTS.getOrDefault(template, template);
        String body = render(BODIES.getOrDefault(template, template), payload);
        persist(organizationId, to, template, payload);
        try {
            if (deliver(to, subject, body)) {
                return SendResult.ok();
            }
            log.info("email stored in outbox template={} to={} subject={}", template, to, subject);
            return SendResult.queued();
        } catch (Exception ex) {
            log.warn("email delivery failed template={} to={}: {}", template, to, ex.getMessage());
            return SendResult.failed(userFacingMailError(ex));
        }
    }

    public record SendResult(boolean delivered, String warning) {
        static SendResult ok() {
            return new SendResult(true, null);
        }

        static SendResult queued() {
            return new SendResult(false, "The invite was saved. Email is waiting in the outbox until delivery succeeds.");
        }

        static SendResult failed(String warning) {
            return new SendResult(false, warning);
        }
    }

    static boolean permanentDeliveryFailure(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("only send testing emails") || text.contains("verify a domain at resend.com");
    }

    static String userFacingMailError(Throwable error) {
        String message = flattenMailError(error);
        if (permanentDeliveryFailure(message)) {
            return "Resend is in test mode. It can only send to the account owner's email until you verify a domain at resend.com/domains.";
        }
        return "The user was created, but the invite email could not be sent. Copy the temporary password below.";
    }

    static String flattenMailError(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(current.getMessage());
            }
            current = current.getCause();
        }
        return text.toString();
    }

    public boolean deliver(String template, String to, Map<String, Object> data) {
        String subject = SUBJECTS.getOrDefault(template, template);
        String body = render(BODIES.getOrDefault(template, template), data);
        return deliver(to, subject, body);
    }

    static String render(String template, Map<String, Object> data) {
        String result = template;
        if (data == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Map<String, Object> withInviteDefaults(String template, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (data != null) {
            payload.putAll(data);
        }
        if ("user-invited".equals(template) && !payload.containsKey("login_url")) {
            payload.put("login_url", appProperties.frontendOrigin() + "/login");
        }
        return payload;
    }

    private void persist(UUID organizationId, String to, String template, Map<String, Object> data) {
        if (!supabaseProperties.configured()) {
            return;
        }
        SupabaseAdminClient client = supabaseAdminClient.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            client.enqueueEmail(organizationId, to, template, data);
        } catch (Exception ex) {
            log.warn("email outbox insert failed: {}", ex.getMessage());
        }
    }

    private boolean deliver(String to, String subject, String body) {
        if (appProperties.resendApiKey() != null && !appProperties.resendApiKey().isBlank()) {
            sendWithResend(to, subject, body);
            return true;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (appProperties.mailEnabled() && sender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appProperties.mailFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("email sent via smtp to={}", to);
            return true;
        }
        return false;
    }

    private void sendWithResend(String to, String subject, String body) {
        RestClient.create()
                .post()
                .uri("https://api.resend.com/emails")
                .header("Authorization", "Bearer " + appProperties.resendApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", appProperties.mailFrom(),
                        "to", new String[] { to },
                        "subject", subject,
                        "text", body
                ))
                .retrieve()
                .toBodilessEntity();
        log.info("email sent via resend to={}", to);
    }
}
