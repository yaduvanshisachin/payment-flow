package com.paymentorchestrator.payment.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls outbox_events for PENDING rows and publishes them to Kafka.
 *
 * Topic = event type (e.g. "payment.created"), key = aggregate id, so all
 * events for the same payment land on the same partition and are delivered
 * in order to any one consumer.
 *
 * Publishing is done synchronously (.get() on the Kafka future) inside the
 * same transaction that locked the rows (see
 * OutboxEventRepository#lockNextBatchForPublishing). If the send fails, we
 * simply don't mark the row PUBLISHED and let the transaction roll back that
 * row back to visible-and-PENDING - the next poll retries it. This is what
 * makes the publisher safe to kill at any point: a row is either published
 * AND marked PUBLISHED, or neither happened.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatchForPublishing(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception e) {
                // Deliberately not rethrowing: one bad event shouldn't block
                // the rest of the batch. Leave it PENDING (don't call
                // markPublished) and it's picked up again on the next poll.
                log.warn("Failed to publish outbox event {} (type={}), will retry next poll: {}",
                        event.getId(), event.getEventType(), e.getMessage());
            }
        }
    }
}
