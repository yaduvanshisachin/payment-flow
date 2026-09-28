package com.paymentorchestrator.payment.dto;

import com.paymentorchestrator.payment.domain.Payment;
import com.paymentorchestrator.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String merchantId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt,
        boolean replayed
) {
    public static PaymentResponse of(Payment payment, boolean replayed) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                replayed
        );
    }
}
