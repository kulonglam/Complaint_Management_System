package com.cms.backend.controller;

import com.cms.backend.service.RetentionJobService;
import com.cms.backend.service.SlaJobService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlaJobController {

    private final SlaJobService slaJobService;
    private final RetentionJobService retentionJobService;

    public SlaJobController(SlaJobService slaJobService, RetentionJobService retentionJobService) {
        this.slaJobService = slaJobService;
        this.retentionJobService = retentionJobService;
    }

    @PostMapping({"/api/v1/jobs/sla", "/api/jobs/sla"})
    @PreAuthorize("hasRole('JOB')")
    public Map<String, Integer> processSla(@RequestHeader(value = "x-job-key", required = false) String jobKey) {
        return slaJobService.process(jobKey);
    }

    @PostMapping({"/api/v1/jobs/retention", "/api/jobs/retention"})
    @PreAuthorize("hasRole('JOB')")
    public Map<String, Integer> processRetention(@RequestHeader(value = "x-job-key", required = false) String jobKey) {
        return retentionJobService.process(jobKey);
    }
}
