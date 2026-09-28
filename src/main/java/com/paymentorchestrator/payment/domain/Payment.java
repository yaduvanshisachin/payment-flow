package com.paymentorchestrator.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single payment attempt, tracked through its lifecycle.
 *
 * Deliberately no public setStatus(): the only way to change status is
 * {@link #transitionTo(PaymentStatus)}, which routes through
 * {@link PaymentStateMachine} so an illegal transition fails loudly instead
 * of silently corrupting state. Everything else about a payment (amount,
 * currency, merchant, idempotency key) is immutable after creation - if you
 * need to change those, you're describing a new payment.
 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
)
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public Payment(String idempotencyKey, String merchantId, BigDecimal amount, String currency) {
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionTo(PaymentStatus newStatus) {
        PaymentStateMachine.validateTransition(this.status, newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
