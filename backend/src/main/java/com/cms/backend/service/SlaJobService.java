package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import com.cms.backend.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SlaJobService {

    private static final Logger log = LoggerFactory.getLogger(SlaJobService.class);

    private final AppProperties appProperties;
    private final SupabaseProperties supabaseProperties;
    private final SupabaseAdminClient supabaseAdminClient;

    public SlaJobService(
            AppProperties appProperties,
            SupabaseProperties supabaseProperties,
            SupabaseAdminClient supabaseAdminClient
    ) {
        this.appProperties = appProperties;
        this.supabaseProperties = supabaseProperties;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    public Map<String, Integer> process(String jobKey) {
        if (!jobKeyMatches(jobKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return Map.of("processed", supabaseAdminClient.processSlaJobs());
    }

    @Scheduled(cron = "${app.sla-cron:0 */15 * * * *}")
    public void processOnSchedule() {
        if (!appProperties.slaEnabled() || !supabaseProperties.configured()) {
            return;
        }
        try {
            int processed = supabaseAdminClient.processSlaJobs();
            log.info("SLA job processed {} record(s)", processed);
        } catch (ApiException ex) {
            log.warn("SLA job skipped: {}", ex.getMessage());
        }
    }

    private boolean jobKeyMatches(String jobKey) {
        String expected = appProperties.jobSecret();
        if (jobKey == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                jobKey.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}
