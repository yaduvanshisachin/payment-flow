package com.paymentorchestrator.payment.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row = one domain event waiting to (or already) published to Kafka.
 *
 * Written inside the SAME transaction as the business change it describes
 * (see OutboxService#record) - that's the entire point of the outbox
 * pattern. A separate process (OutboxPublisher) is the only thing that ever
 * marks a row PUBLISHED; nothing else should touch status after creation.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    // Stored as plain JSON text rather than a JSONB-mapped type to avoid
    // pulling in a Hibernate JSON type library for week 2. Nothing here
    // queries *inside* the payload, so TEXT is fine - if that changes later,
    // this is the column to switch to a proper jsonb mapping.
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
