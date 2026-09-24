package com.cms.backend.service;

import com.cms.backend.config.AppProperties;
import com.cms.backend.exception.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SlaJobService {

    private final AppProperties appProperties;
    private final SupabaseAdminClient supabaseAdminClient;

    public SlaJobService(AppProperties appProperties, SupabaseAdminClient supabaseAdminClient) {
        this.appProperties = appProperties;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    public Map<String, Integer> process(String jobKey) {
        if (jobKey == null || !jobKey.equals(appProperties.getJobSecret())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        try {
            return Map.of("processed", supabaseAdminClient.processSlaJobs());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process SLA jobs.");
        }
    }
}
