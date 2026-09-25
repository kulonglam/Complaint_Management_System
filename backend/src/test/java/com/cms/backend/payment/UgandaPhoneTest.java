package com.cms.backend.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UgandaPhoneTest {

    @Test
    void normalizesLocalAndInternationalNumbers() {
        assertEquals("256770123456", UgandaPhone.toMsisdn("0770 123 456"));
        assertEquals("256770123456", UgandaPhone.toMsisdn("+256770123456"));
        assertEquals("770123456", UgandaPhone.national("0770123456"));
    }

    @Test
    void rejectsShortNumbers() {
        assertThrows(IllegalArgumentException.class, () -> UgandaPhone.toMsisdn("123"));
    }

    @Test
    void recognisesLocalNetworks() {
        assertTrue(UgandaPhone.looksLikeMtn("0770123456"));
        assertTrue(UgandaPhone.looksLikeAirtel("0750123456"));
    }
}
