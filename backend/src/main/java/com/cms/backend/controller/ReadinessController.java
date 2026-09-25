package com.cms.backend.controller;

import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {

    private final SupabaseProperties supabaseProperties;
    private final AppProperties appProperties;

    public ReadinessController(SupabaseProperties supabaseProperties, AppProperties appProperties) {
        this.supabaseProperties = supabaseProperties;
        this.appProperties = appProperties;
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean supabase = supabaseProperties.configured();
        boolean mail = appProperties.mailEnabled()
                || (appProperties.resendApiKey() != null && !appProperties.resendApiKey().isBlank());
        boolean sentry = System.getenv("SENTRY_DSN") != null && !System.getenv("SENTRY_DSN").isBlank();
        Map<String, Object> body = Map.of(
                "ok", supabase,
                "supabase", supabase,
                "mail_delivery", mail,
                "sentry", sentry,
                "payments", Map.of(
                        "currency", appProperties.paymentCurrency(),
                        "mtn", appProperties.mtnConfigured(),
                        "airtel", appProperties.airtelConfigured(),
                        "pesapal", appProperties.pesapalConfigured()
                )
        );
        return ResponseEntity.status(supabase ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
