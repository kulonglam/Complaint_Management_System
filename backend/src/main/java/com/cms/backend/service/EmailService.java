package com.cms.backend.service;

import com.cms.backend.config.AppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final Map<String, String> SUBJECTS = Map.of(
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

    private static final Map<String, String> BODIES = Map.of(
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
    private final ObjectProvider<JavaMailSender> mailSender;

    public EmailService(AppProperties appProperties, ObjectProvider<JavaMailSender> mailSender) {
        this.appProperties = appProperties;
        this.mailSender = mailSender;
    }

    public void send(String template, String to, Map<String, Object> data) {
        String subject = SUBJECTS.getOrDefault(template, template);
        String body = render(BODIES.getOrDefault(template, template), data);
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!appProperties.mailEnabled() || sender == null) {
            log.info("email queued locally template={} to={} subject={}", template, to, subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("email sent template={} to={}", template, to);
    }

    private static String render(String template, Map<String, Object> data) {
        String result = template;
        if (data == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
