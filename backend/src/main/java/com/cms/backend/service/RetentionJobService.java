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
public class RetentionJobService {

    private static final Logger log = LoggerFactory.getLogger(RetentionJobService.class);

    private final AppProperties appProperties;
    private final SupabaseProperties supabaseProperties;
    private final SupabaseAdminClient supabaseAdminClient;

    public RetentionJobService(
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
        return Map.of("processed", supabaseAdminClient.purgeExpiredRecords());
    }

    @Scheduled(cron = "${app.retention-cron:0 30 2 * * *}")
    public void processOnSchedule() {
        if (!appProperties.retentionEnabled() || !supabaseProperties.configured()) {
            return;
        }
        try {
            int processed = supabaseAdminClient.purgeExpiredRecords();
            log.info("retention job redacted {} complaint(s)", processed);
        } catch (Exception ex) {
            log.warn("retention job skipped: {}", ex.getMessage());
        }
    }

    private boolean jobKeyMatches(String jobKey) {
        String expected = appProperties.jobSecret();
        return jobKey != null
                && expected != null
                && MessageDigest.isEqual(
                        jobKey.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8)
                );
    }
}
