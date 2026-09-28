package com.paymentorchestrator.payment.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for which payment status transitions are legal.
 *
 * Nothing outside this class should decide whether a transition is allowed -
 * every place that changes a payment's status (API handlers, PSP callback
 * consumers, reconciliation jobs, retry schedulers) must route through
 * {@link #validateTransition(PaymentStatus, PaymentStatus)}. That's what
 * makes it possible to trust the state machine: there's exactly one place
 * the rules live.
 *
 * Notes on the shape of the graph:
 *  - FAILED -> PENDING models a bounded retry (a new attempt at the PSP).
 *    The retry *count* and cutoff live in the service layer, not here -
 *    this class only knows what's structurally legal, not how many times
 *    it has already happened.
 *  - TIMED_OUT is a first-class state, not an error. A network timeout
 *    doesn't tell you whether the PSP actually processed the payment, so
 *    TIMED_OUT can resolve to any of PENDING (retry), CAPTURED/AUTHORIZED
 *    (turns out it succeeded), or FAILED (turns out it didn't) - typically
 *    decided by the reconciliation job querying the PSP for ground truth.
 *  - CAPTURED and REFUNDED are otherwise terminal-ish: no further money
 *    movement is modelled here beyond a single refund.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITIONS.put(PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PENDING));
        TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(
                PaymentStatus.AUTHORIZED,
                PaymentStatus.FAILED,
                PaymentStatus.TIMED_OUT));
        TRANSITIONS.put(PaymentStatus.AUTHORIZED, EnumSet.of(
                PaymentStatus.CAPTURED,
                PaymentStatus.FAILED));
        TRANSITIONS.put(PaymentStatus.CAPTURED, EnumSet.of(PaymentStatus.REFUNDED));
        TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.of(PaymentStatus.PENDING));
        TRANSITIONS.put(PaymentStatus.TIMED_OUT, EnumSet.of(
                PaymentStatus.PENDING,
                PaymentStatus.AUTHORIZED,
                PaymentStatus.CAPTURED,
                PaymentStatus.FAILED));
        TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {
    }

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }

    public static Set<PaymentStatus> allowedNextStates(PaymentStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}
