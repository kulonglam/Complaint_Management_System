package com.cms.backend.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail-enabled", havingValue = "true")
    JavaMailSender javaMailSender(org.springframework.core.env.Environment environment) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(environment.getProperty("spring.mail.host", ""));
        sender.setPort(Integer.parseInt(environment.getProperty("spring.mail.port", "587")));
        sender.setUsername(environment.getProperty("spring.mail.username"));
        sender.setPassword(environment.getProperty("spring.mail.password"));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
