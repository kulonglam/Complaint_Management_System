package com.cms.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailServiceTest {

    @Test
    void renderSubstitutesPlaceholders() {
        String body = EmailService.render("Hello {first_name}", Map.of("first_name", "Amina"));
        assertEquals("Hello Amina", body);
    }

    @Test
    void renderLeavesUnknownPlaceholders() {
        assertEquals("Hello {name}", EmailService.render("Hello {name}", Map.of()));
    }
}
