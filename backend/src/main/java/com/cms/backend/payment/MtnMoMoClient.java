package com.cms.backend.payment;

import com.cms.backend.config.AppProperties;
import com.cms.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MtnMoMoClient {

    private final AppProperties app;
    private final RestClient restClient;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public MtnMoMoClient(AppProperties app) {
        this.app = app;
        this.restClient = RestClient.create();
    }

    public boolean configured() {
        return app.mtnConfigured();
    }

    public String requestToPay(String merchantRef, int amount, String msisdn) {
        String reference = UUID.randomUUID().toString();
        try {
            restClient.post()
                    .uri(baseUrl() + "/collection/v1_0/requesttopay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(token());
                        headers.set("Ocp-Apim-Subscription-Key", app.mtnSubscriptionKey());
                        headers.set("X-Reference-Id", reference);
                        headers.set("X-Target-Environment", app.mtnTargetEnvironment());
                    })
                    .body(Map.of(
                            "amount", String.valueOf(amount),
                            "currency", "UGX",
                            "externalId", merchantRef,
                            "payer", Map.of("partyIdType", "MSISDN", "partyId", msisdn),
                            "payerMessage", "Complaint Management subscription",
                            "payeeNote", merchantRef
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MTN Mobile Money could not start this payment.");
        }
        return reference;
    }

    public String status(String reference) {
        try {
            JsonNode body = restClient.get()
                    .uri(baseUrl() + "/collection/v1_0/requesttopay/" + reference)
                    .headers(headers -> {
                        headers.setBearerAuth(token());
                        headers.set("Ocp-Apim-Subscription-Key", app.mtnSubscriptionKey());
                        headers.set("X-Target-Environment", app.mtnTargetEnvironment());
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return body == null ? "" : body.path("status").asText("").toUpperCase();
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to read the MTN payment status.");
        }
    }

    private String token() {
        if (token != null && Instant.now().isBefore(tokenExpires.minusSeconds(15))) {
            return token;
        }
        String basic = Base64.getEncoder().encodeToString(
                (app.mtnApiUser() + ":" + app.mtnApiKey()).getBytes(StandardCharsets.UTF_8)
        );
        try {
            JsonNode body = restClient.post()
                    .uri(baseUrl() + "/collection/token/")
                    .header("Authorization", "Basic " + basic)
                    .header("Ocp-Apim-Subscription-Key", app.mtnSubscriptionKey())
                    .retrieve()
                    .body(JsonNode.class);
            token = body == null ? "" : body.path("access_token").asText("");
            int expires = body == null ? 300 : body.path("expires_in").asInt(300);
            tokenExpires = Instant.now().plusSeconds(Math.max(30, expires));
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to authenticate with MTN Mobile Money.");
        }
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to authenticate with MTN Mobile Money.");
        }
        return token;
    }

    private String baseUrl() {
        return "sandbox".equalsIgnoreCase(app.mtnTargetEnvironment())
                ? "https://sandbox.momodeveloper.mtn.com"
                : "https://proxy.momoapi.mtn.com";
    }
}
