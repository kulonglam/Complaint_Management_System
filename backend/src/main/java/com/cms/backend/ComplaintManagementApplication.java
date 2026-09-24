package com.cms.backend;

import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, SupabaseProperties.class})
public class ComplaintManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplaintManagementApplication.class, args);
    }
}
