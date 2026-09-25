package com.cms.backend.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InviteErrorMessageTest {

    @Test
    void mapsDuplicateEmail() {
        String message = SupabaseAdminClient.inviteErrorMessage(
                "A user with this email address has already been registered"
        );
        assertTrue(message.contains("already has an account"));
    }

    @Test
    void keepsPlanLimit() {
        String original = "This organization has reached its user limit for the Trial plan";
        assertEquals(original, SupabaseAdminClient.inviteErrorMessage(original));
    }
}
