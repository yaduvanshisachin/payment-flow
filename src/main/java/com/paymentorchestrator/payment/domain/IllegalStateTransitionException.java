package com.paymentorchestrator.payment.domain;

/**
 * Thrown when code attempts to move a payment to a status that is not
 * reachable from its current status. This is a domain rule violation,
 * not a bug to swallow - callers should surface it (typically as HTTP 409).
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public IllegalStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Illegal payment state transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public PaymentStatus getFrom() {
        return from;
    }

    public PaymentStatus getTo() {
        return to;
    }
}
