package com.paymentorchestrator.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The payload of a "payment.created" event. This is the contract between
 * the producer (OutboxService.record call in PaymentService) and any
 * consumer (PaymentEventConsumer here, but in principle any other service
 * that cares about new payments - ledger, notifications, etc. in later
 * weeks).
 *
 * eventId is distinct from paymentId: it identifies THIS event instance,
 * and is what consumers use for deduplication (see ProcessedEvent) - a
 * given payment could in principle have more than one "payment.created"
 * event in a more complex flow, though not in week 2.
 */
public record PaymentCreatedPayload(
        UUID eventId,
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String status,
        Instant occurredAt
) {
}
