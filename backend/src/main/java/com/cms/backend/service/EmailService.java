package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
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
            "user-invited", "Hello {first_name}, you have been invited to the complaint platform."
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

    public void send(String template, String to, Map<String, Object> data, UUID organizationId) {
        String subject = SUBJECTS.getOrDefault(template, template);
        String body = render(BODIES.getOrDefault(template, template), data);
        persist(organizationId, to, template, data);
        if (!deliver(to, subject, body)) {
            log.info("email stored in outbox template={} to={} subject={}", template, to, subject);
        }
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
