package com.paymentorchestrator.payment.dto;

import com.paymentorchestrator.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(
        @NotNull(message = "targetStatus is required")
        PaymentStatus targetStatus
) {
}
