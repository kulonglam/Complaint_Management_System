package com.cms.backend.controller;

import com.cms.backend.service.SlaJobService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlaJobController {

    private final SlaJobService slaJobService;

    public SlaJobController(SlaJobService slaJobService) {
        this.slaJobService = slaJobService;
    }

    @PostMapping("/api/jobs/sla")
    public Map<String, Integer> processSla(@RequestHeader(value = "x-job-key", required = false) String jobKey) {
        return slaJobService.process(jobKey);
    }
}
