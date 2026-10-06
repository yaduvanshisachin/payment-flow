package com.paymentorchestrator.payment.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a given (eventId, consumerName) pair has already been
 * handled. Kafka with manual offset commit gives at-least-once delivery -
 * a crash between "processed successfully" and "offset committed" causes
 * redelivery of the same message. This table is what makes that redelivery
 * a no-op instead of a double-processed event (see PaymentEventConsumer).
 */
@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_events_event_consumer",
                columnNames = {"event_id", "consumer_name"}
        )
)
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, updatable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId, String consumerName) {
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    @PrePersist
    void onCreate() {
        this.processedAt = Instant.now();
    }
}
