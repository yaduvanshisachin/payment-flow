package com.paymentorchestrator.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PaymentStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "CREATED, PENDING",
            "PENDING, AUTHORIZED",
            "PENDING, FAILED",
            "PENDING, TIMED_OUT",
            "AUTHORIZED, CAPTURED",
            "AUTHORIZED, FAILED",
            "CAPTURED, REFUNDED",
            "FAILED, PENDING",
            "TIMED_OUT, PENDING",
            "TIMED_OUT, AUTHORIZED",
            "TIMED_OUT, CAPTURED",
            "TIMED_OUT, FAILED",
    })
    void allowsLegalTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(PaymentStateMachine.canTransition(from, to)).isTrue();
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "CREATED, AUTHORIZED",
            "CREATED, CAPTURED",
            "PENDING, CAPTURED",
            "PENDING, REFUNDED",
            "CAPTURED, PENDING",
            "CAPTURED, FAILED",
            "REFUNDED, CAPTURED",
            "FAILED, CAPTURED",
    })
    void rejectsIllegalTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(PaymentStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(from, to))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void refundedHasNoOutgoingTransitions() {
        assertThat(PaymentStateMachine.allowedNextStates(PaymentStatus.REFUNDED)).isEmpty();
    }

    @Test
    void everyStatusIsReachableAndHasDefinedTransitions() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(PaymentStateMachine.allowedNextStates(status)).isNotNull();
        }
    }
}
