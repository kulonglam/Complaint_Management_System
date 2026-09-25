package com.cms.backend.dto;

import java.util.UUID;

public record CreatePaymentRequest(
        UUID planId,
        String paymentMethod,
        String phone
) {
}
