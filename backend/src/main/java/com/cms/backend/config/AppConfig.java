package com.cms.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    RestClient supabaseRestClient(SupabaseProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        String baseUrl = properties.url() == null ? "" : properties.url();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("apikey", properties.serviceRoleKey())
                .defaultHeader("Authorization", "Bearer " + properties.serviceRoleKey())
                .build();
    }
}
