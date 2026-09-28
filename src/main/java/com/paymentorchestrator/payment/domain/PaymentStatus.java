package com.paymentorchestrator.payment.domain;

/**
 * Lifecycle states of a payment.
 *
 * CREATED    - persisted, not yet submitted for processing
 * PENDING    - submitted to a PSP, awaiting a result
 * AUTHORIZED - funds reserved by the issuer, not yet captured
 * CAPTURED   - funds claimed by the merchant (money has effectively moved)
 * FAILED     - declined or otherwise terminally rejected
 * TIMED_OUT  - no response received from the PSP in time; true outcome unknown
 *              until reconciliation resolves it
 * REFUNDED   - captured funds returned to the customer
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    TIMED_OUT,
    REFUNDED
}
