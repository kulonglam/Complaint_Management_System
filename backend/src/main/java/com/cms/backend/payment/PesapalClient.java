package com.cms.backend.payment;

import com.cms.backend.config.AppProperties;
import com.cms.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PesapalClient {

    private final AppProperties app;
    private final RestClient restClient;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public PesapalClient(AppProperties app) {
        this.app = app;
        this.restClient = RestClient.create();
    }

    public boolean configured() {
        return app.pesapalConfigured();
    }

    public Map<String, String> submitOrder(String merchantRef, int amount, String description, String email, String phone, String firstName) {
        String access = requestToken();
        String ipn = app.pesapalIpnId();
        if (ipn == null || ipn.isBlank()) {
            ipn = registerIpn(access);
        }
        JsonNode response = post(access, "/Transactions/SubmitOrderRequest", Map.of(
                "id", merchantRef,
                "currency", "UGX",
                "amount", amount,
                "description", description,
                "callback_url", app.frontendOrigin() + "/billing/return",
                "notification_id", ipn,
                "billing_address", Map.of(
                        "email_address", email == null ? "" : email,
                        "phone_number", phone == null ? "" : phone,
                        "country_code", "UG",
                        "first_name", firstName == null || firstName.isBlank() ? "Billing" : firstName
                )
        ));
        String tracking = text(response, "order_tracking_id");
        String redirect = text(response, "redirect_url");
        if (tracking.isBlank() || redirect.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Pesapal did not return a checkout URL.");
        }
        return Map.of("provider_ref", tracking, "checkout_url", redirect);
    }

    public String transactionStatus(String orderTrackingId) {
        String access = requestToken();
        JsonNode response = get(access, "/Transactions/GetTransactionStatus?orderTrackingId=" + orderTrackingId);
        return text(response, "payment_status_description").toUpperCase();
    }

    private String requestToken() {
        if (token != null && Instant.now().isBefore(tokenExpires.minusSeconds(30))) {
            return token;
        }
        JsonNode response = restClient.post()
                .uri(baseUrl() + "/Auth/RequestToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "consumer_key", app.pesapalConsumerKey(),
                        "consumer_secret", app.pesapalConsumerSecret()
                ))
                .retrieve()
                .body(JsonNode.class);
        token = text(response, "token");
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to authenticate with Pesapal.");
        }
        tokenExpires = Instant.now().plusSeconds(300);
        return token;
    }

    private String registerIpn(String access) {
        JsonNode response = post(access, "/URLSetup/RegisterIPN", Map.of(
                "url", app.publicApiUrl() + "/api/v1/payments/ipn/pesapal",
                "ipn_notification_type", "GET"
        ));
        String id = text(response, "ipn_id");
        if (id.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Register a Pesapal IPN URL and set PESAPAL_IPN_ID.");
        }
        return id;
    }

    private JsonNode post(String access, String path, Object body) {
        try {
            return restClient.post()
                    .uri(baseUrl() + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + access)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Pesapal rejected the request.");
        }
    }

    private JsonNode get(String access, String path) {
        try {
            return restClient.get()
                    .uri(baseUrl() + path)
                    .header("Authorization", "Bearer " + access)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to read the Pesapal payment status.");
        }
    }

    private String baseUrl() {
        return "live".equalsIgnoreCase(app.pesapalEnvironment())
                ? "https://pay.pesapal.com/v3/api"
                : "https://cybqa.pesapal.com/pesapalv3/api";
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() ? "" : node.path(field).asText("");
    }
}
