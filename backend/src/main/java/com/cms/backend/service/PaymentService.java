package com.cms.backend.service;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.dto.CreatePaymentRequest;
import com.cms.backend.exception.ApiException;
import com.cms.backend.payment.AirtelMoneyClient;
import com.cms.backend.payment.MtnMoMoClient;
import com.cms.backend.payment.PesapalClient;
import com.cms.backend.payment.UgandaPhone;
import com.cms.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final AppProperties app;
    private final SupabaseAdminClient supabase;
    private final PesapalClient pesapal;
    private final MtnMoMoClient mtn;
    private final AirtelMoneyClient airtel;

    public PaymentService(
            AppProperties app,
            SupabaseAdminClient supabase,
            PesapalClient pesapal,
            MtnMoMoClient mtn,
            AirtelMoneyClient airtel
    ) {
        this.app = app;
        this.supabase = supabase;
        this.pesapal = pesapal;
        this.mtn = mtn;
        this.airtel = airtel;
    }

    public Map<String, Object> methods() {
        return Map.of(
                "currency", app.paymentCurrency(),
                "demo", app.paymentDemo() || !anyConfigured(),
                "methods", Map.of(
                        "MTN", Map.of("label", "MTN Mobile Money", "configured", mtn.configured(), "needs_phone", true),
                        "AIRTEL", Map.of("label", "Airtel Money", "configured", airtel.configured(), "needs_phone", true),
                        "PESAPAL", Map.of("label", "Pesapal", "configured", pesapal.configured(), "needs_phone", false)
                )
        );
    }

    public Map<String, Object> create(AuthenticatedUser actor, CreatePaymentRequest request) {
        requireOrg(actor);
        if (request == null || request.planId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a plan.");
        }
        String provider = normalizeProvider(request.paymentMethod());
        JsonNode plan = supabase.getById("subscription_plans", request.planId(), "id,name,price_cents,currency,billing_interval");
        int amount = plan.path("price_cents").asInt(0);
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The trial plan does not require payment.");
        }
        String phone = null;
        if ("MTN".equals(provider) || "AIRTEL".equals(provider)) {
            try {
                phone = UgandaPhone.toMsisdn(request.phone());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        } else if (request.phone() != null && !request.phone().isBlank()) {
            try {
                phone = UgandaPhone.toMsisdn(request.phone());
            } catch (IllegalArgumentException ignored) {
                phone = request.phone();
            }
        }

        String merchantRef = "CMS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("organization_id", actor.organizationId().toString());
        row.put("plan_id", request.planId().toString());
        row.put("created_by", actor.id().toString());
        row.put("provider", provider);
        row.put("amount", amount);
        row.put("currency", plan.path("currency").asText("UGX"));
        row.put("phone", phone);
        row.put("status", "PENDING");
        row.put("merchant_ref", merchantRef);
        JsonNode created = supabase.insertReturning("payment_intents", row);
        UUID paymentId = UUID.fromString(created.path("id").asText());

        boolean demo = app.paymentDemo() || !providerConfigured(provider);
        Map<String, Object> extras = new HashMap<>();
        if (!demo) {
            extras.putAll(startProvider(provider, merchantRef, amount, plan.path("name").asText("Plan"), actor, phone));
            if (!extras.isEmpty()) {
                supabase.patchById("payment_intents", paymentId, extras);
            }
        } else {
            extras.put("status", "PENDING");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", paymentId.toString());
        result.put("provider", provider);
        result.put("amount", amount);
        result.put("currency", plan.path("currency").asText("UGX"));
        result.put("status", extras.getOrDefault("status", "PENDING"));
        result.put("checkout_url", extras.get("checkout_url"));
        result.put("demo", demo);
        result.put("message", demo
                ? "Provider keys are not configured. Confirm this payment to activate the plan in demo mode."
                : providerMessage(provider));
        return result;
    }

    public Map<String, Object> status(AuthenticatedUser actor, UUID id) {
        JsonNode payment = load(actor, id);
        String current = payment.path("status").asText();
        if (!"PAID".equals(current) && !"FAILED".equals(current) && !"CANCELLED".equals(current)) {
            current = refresh(payment);
        }
        return Map.of(
                "id", payment.path("id").asText(),
                "status", current,
                "provider", payment.path("provider").asText(),
                "amount", payment.path("amount").asInt(),
                "currency", payment.path("currency").asText("UGX"),
                "checkout_url", payment.path("checkout_url").asText(null)
        );
    }

    public Map<String, Object> confirmDemo(AuthenticatedUser actor, UUID id) {
        if (!app.paymentDemo() && anyConfigured()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Demo confirmation is disabled when live payment keys are set.");
        }
        JsonNode payment = load(actor, id);
        markPaid(payment);
        return Map.of("id", id.toString(), "status", "PAID", "demo", true);
    }

    public void handlePesapalIpn(String orderTrackingId, String merchantRef) {
        String filter = orderTrackingId != null && !orderTrackingId.isBlank()
                ? "&provider_ref=eq." + orderTrackingId
                : (merchantRef != null && !merchantRef.isBlank() ? "&merchant_ref=eq." + merchantRef : "");
        if (filter.isBlank()) {
            return;
        }
        JsonNode rows = supabase.list(
                "/rest/v1/payment_intents?select=id,organization_id,plan_id,provider,status,provider_ref,merchant_ref"
                        + filter,
                0,
                1
        ).data();
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return;
        }
        refresh(rows.get(0));
    }

    private Map<String, Object> startProvider(
            String provider,
            String merchantRef,
            int amount,
            String planName,
            AuthenticatedUser actor,
            String phone
    ) {
        return switch (provider) {
            case "PESAPAL" -> {
                Map<String, String> started = pesapal.submitOrder(
                        merchantRef,
                        amount,
                        "Subscription: " + planName,
                        actor.email(),
                        phone,
                        actor.email()
                );
                yield Map.of(
                        "provider_ref", started.get("provider_ref"),
                        "checkout_url", started.get("checkout_url"),
                        "status", "PROCESSING"
                );
            }
            case "MTN" -> Map.of(
                    "provider_ref", mtn.requestToPay(merchantRef, amount, phone),
                    "status", "PROCESSING"
            );
            case "AIRTEL" -> Map.of(
                    "provider_ref", airtel.collect(merchantRef, amount, UgandaPhone.national(phone)),
                    "status", "PROCESSING"
            );
            default -> Map.of();
        };
    }

    private String refresh(JsonNode payment) {
        UUID id = UUID.fromString(payment.path("id").asText());
        String provider = payment.path("provider").asText();
        String ref = payment.path("provider_ref").asText("");
        if (ref.isBlank()) {
            return payment.path("status").asText("PENDING");
        }
        String remote = switch (provider) {
            case "PESAPAL" -> pesapal.configured() ? pesapal.transactionStatus(ref) : "";
            case "MTN" -> mtn.configured() ? mtn.status(ref) : "";
            case "AIRTEL" -> airtel.configured() ? airtel.status(ref) : "";
            default -> "";
        };
        if (isPaid(remote)) {
            markPaid(payment);
            return "PAID";
        }
        if (isFailed(remote)) {
            supabase.patchById("payment_intents", id, Map.of("status", "FAILED", "failure_reason", remote, "updated_at", OffsetDateTime.now().toString()));
            return "FAILED";
        }
        if ("PROCESSING".equals(payment.path("status").asText())) {
            return "PROCESSING";
        }
        supabase.patchById("payment_intents", id, Map.of("status", "PROCESSING", "updated_at", OffsetDateTime.now().toString()));
        return "PROCESSING";
    }

    private void markPaid(JsonNode payment) {
        UUID paymentId = UUID.fromString(payment.path("id").asText());
        UUID orgId = UUID.fromString(payment.path("organization_id").asText());
        UUID planId = UUID.fromString(payment.path("plan_id").asText());
        supabase.patchById("payment_intents", paymentId, Map.of(
                "status", "PAID",
                "paid_at", OffsetDateTime.now().toString(),
                "updated_at", OffsetDateTime.now().toString()
        ));
        supabase.patch(
                "organization_subscriptions?organization_id=eq." + orgId + "&status=eq.ACTIVE",
                Map.of("status", "INACTIVE")
        );
        Map<String, Object> subscription = new HashMap<>();
        subscription.put("organization_id", orgId.toString());
        subscription.put("plan_id", planId.toString());
        subscription.put("status", "ACTIVE");
        subscription.put("payment_provider", payment.path("provider").asText());
        subscription.put("last_payment_id", paymentId.toString());
        subscription.put("current_period_end", OffsetDateTime.now().plusMonths(1).toString());
        supabase.insertReturning("organization_subscriptions", subscription);
    }

    private JsonNode load(AuthenticatedUser actor, UUID id) {
        JsonNode payment = supabase.getById(
                "payment_intents",
                id,
                "id,organization_id,plan_id,provider,status,provider_ref,merchant_ref,checkout_url,amount,currency"
        );
        if (!actor.platformAdmin() && !actor.organizationId().toString().equals(payment.path("organization_id").asText())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found");
        }
        return payment;
    }

    private void requireOrg(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You are not assigned to an organization.");
        }
    }

    private boolean anyConfigured() {
        return pesapal.configured() || mtn.configured() || airtel.configured();
    }

    private boolean providerConfigured(String provider) {
        return switch (provider) {
            case "PESAPAL" -> pesapal.configured();
            case "MTN" -> mtn.configured();
            case "AIRTEL" -> airtel.configured();
            default -> false;
        };
    }

    private static String normalizeProvider(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
        if ("MTN".equals(value) || "AIRTEL".equals(value) || "PESAPAL".equals(value)) {
            return value;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Choose MTN, Airtel, or Pesapal.");
    }

    private static String providerMessage(String provider) {
        return switch (provider) {
            case "MTN" -> "Approve the MTN prompt on the phone to complete payment.";
            case "AIRTEL" -> "Approve the Airtel Money prompt on the phone to complete payment.";
            default -> "Finish payment on the Pesapal checkout page.";
        };
    }

    private static boolean isPaid(String status) {
        return status.contains("COMPLETED") || status.contains("SUCCESS") || "PAID".equals(status);
    }

    private static boolean isFailed(String status) {
        return status.contains("FAILED") || status.contains("INVALID") || status.contains("REJECTED") || status.contains("EXPIRED");
    }
}
