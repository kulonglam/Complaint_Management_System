package com.cms.backend.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final Map<String, String> TEMPLATES = Map.of(
            "complaint-received", "Your complaint has been received.",
            "complaint-assigned", "A complaint was assigned.",
            "complaint-status-updated", "A complaint status changed.",
            "complaint-resolved", "A complaint was resolved.",
            "complaint-closed", "A complaint was closed.",
            "complaint-reopened", "A complaint was reopened.",
            "sla-warning", "A complaint is approaching its SLA.",
            "complaint-overdue", "A complaint is overdue.",
            "user-invited", "You have been invited to the complaint platform."
    );

    public void send(String template, String to, Map<String, Object> data) {
        // Integration point: connect SMTP, Resend, Postmark, or SES here.
        log.info("email skipped template={} to={} subject={} data={}",
                template, to, TEMPLATES.getOrDefault(template, template), data);
    }
}
