package com.paymentorchestrator.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotBlank(message = "merchantId is required")
        String merchantId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code, e.g. INR, USD")
        String currency
) {
}
