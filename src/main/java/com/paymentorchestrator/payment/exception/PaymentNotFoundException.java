package com.paymentorchestrator.payment.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super("Payment not found: " + id);
    }
}
