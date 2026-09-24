package com.cms.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({AppProperties.class, SupabaseProperties.class})
public class AppConfig {

    @Bean
    RestClient supabaseRestClient(SupabaseProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader("apikey", properties.getServiceRoleKey())
                .defaultHeader("Authorization", "Bearer " + properties.getServiceRoleKey())
                .build();
    }
}
