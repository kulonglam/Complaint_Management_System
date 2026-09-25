package com.cms.backend.controller;

import com.cms.backend.dto.CreatePaymentRequest;
import com.cms.backend.security.AuthenticatedUser;
import com.cms.backend.service.PaymentService;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentApiController {

    private final PaymentService paymentService;

    public PaymentApiController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/api/v1/payments/methods")
    @PreAuthorize("hasAuthority('settings:view') or hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> methods() {
        return paymentService.methods();
    }

    @PostMapping("/api/v1/payments")
    @PreAuthorize("hasAuthority('settings:update') or hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody CreatePaymentRequest request
    ) {
        return paymentService.create(actor, request);
    }

    @GetMapping("/api/v1/payments/{id}")
    @PreAuthorize("hasAuthority('settings:view') or hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> status(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id
    ) {
        return paymentService.status(actor, id);
    }

    @PostMapping("/api/v1/payments/{id}/demo-confirm")
    @PreAuthorize("hasAuthority('settings:update') or hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> demoConfirm(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id
    ) {
        return paymentService.confirmDemo(actor, id);
    }

    @GetMapping("/api/v1/payments/ipn/pesapal")
    public Map<String, String> pesapalIpn(
            @RequestParam(value = "OrderTrackingId", required = false) String trackingId,
            @RequestParam(value = "OrderMerchantReference", required = false) String merchantRef
    ) {
        paymentService.handlePesapalIpn(trackingId, merchantRef);
        return Map.of("status", "ok");
    }

    @PostMapping("/api/v1/payments/ipn/pesapal")
    public Map<String, String> pesapalIpnPost(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> payload = body == null ? Map.of() : body;
        paymentService.handlePesapalIpn(
                payload.getOrDefault("OrderTrackingId", payload.get("order_tracking_id")),
                payload.getOrDefault("OrderMerchantReference", payload.get("merchant_reference"))
        );
        return Map.of("status", "ok");
    }
}
