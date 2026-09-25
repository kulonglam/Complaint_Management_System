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
public class AirtelMoneyClient {

    private final AppProperties app;
    private final RestClient restClient;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public AirtelMoneyClient(AppProperties app) {
        this.app = app;
        this.restClient = RestClient.create();
    }

    public boolean configured() {
        return app.airtelConfigured();
    }

    public String collect(String merchantRef, int amount, String nationalMsisdn) {
        try {
            JsonNode body = restClient.post()
                    .uri(baseUrl() + "/merchant/v1/payments/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(token());
                        headers.set("X-Country", "UG");
                        headers.set("X-Currency", "UGX");
                    })
                    .body(Map.of(
                            "reference", merchantRef,
                            "subscriber", Map.of(
                                    "country", "UG",
                                    "currency", "UGX",
                                    "msisdn", nationalMsisdn
                            ),
                            "transaction", Map.of(
                                    "amount", amount,
                                    "country", "UG",
                                    "currency", "UGX",
                                    "id", merchantRef
                            )
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            String id = body == null ? "" : body.path("data").path("transaction").path("id").asText(merchantRef);
            return id.isBlank() ? merchantRef : id;
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Airtel Money could not start this payment.");
        }
    }

    public String status(String transactionId) {
        try {
            JsonNode body = restClient.get()
                    .uri(baseUrl() + "/standard/v1/payments/" + transactionId)
                    .headers(headers -> {
                        headers.setBearerAuth(token());
                        headers.set("X-Country", "UG");
                        headers.set("X-Currency", "UGX");
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return body == null ? "" : body.path("data").path("transaction").path("status").asText("").toUpperCase();
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to read the Airtel Money payment status.");
        }
    }

    private String token() {
        if (token != null && Instant.now().isBefore(tokenExpires.minusSeconds(15))) {
            return token;
        }
        try {
            JsonNode body = restClient.post()
                    .uri(baseUrl() + "/auth/oauth2/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", app.airtelClientId(),
                            "client_secret", app.airtelClientSecret(),
                            "grant_type", "client_credentials"
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            token = body == null ? "" : body.path("access_token").asText("");
            int expires = body == null ? 180 : body.path("expires_in").asInt(180);
            tokenExpires = Instant.now().plusSeconds(Math.max(30, expires));
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to authenticate with Airtel Money.");
        }
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to authenticate with Airtel Money.");
        }
        return token;
    }

    private String baseUrl() {
        return "live".equalsIgnoreCase(app.airtelEnvironment())
                ? "https://openapi.airtel.africa"
                : "https://openapiuat.airtel.africa";
    }
}
