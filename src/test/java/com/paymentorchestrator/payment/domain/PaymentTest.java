package com.paymentorchestrator.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void newPaymentStartsInCreatedStatus() {
        Payment payment = new Payment("idem-key-1", "merchant-1", new BigDecimal("100.00"), "INR");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void transitionToUpdatesStatusOnLegalMove() {
        Payment payment = new Payment("idem-key-2", "merchant-1", new BigDecimal("100.00"), "INR");
        payment.transitionTo(PaymentStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void transitionToRejectsIllegalMoveAndLeavesStatusUnchanged() {
        Payment payment = new Payment("idem-key-3", "merchant-1", new BigDecimal("100.00"), "INR");

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.CAPTURED))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }
}
