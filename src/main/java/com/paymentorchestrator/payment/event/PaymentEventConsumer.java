package com.paymentorchestrator.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentorchestrator.payment.domain.PaymentStatus;
import com.paymentorchestrator.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes "payment.created" and moves the payment PENDING - standing in for
 * what will, from week 3 onward, actually be "submit this payment to the PSP
 * gateway". For week 2 the point is purely to prove the pipeline works
 * end-to-end: API writes outbox row -> OutboxPublisher ships it to Kafka ->
 * this consumer picks it up and mutates state, fully decoupled from the HTTP
 * request that created the payment.
 *
 * ack-mode: record (see application.yml) means the offset only commits after
 * this method returns without throwing. If it throws, Kafka redelivers the
 * same record - which is exactly why the dedup check against
 * ProcessedEventRepository comes first, before any state change.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final String CONSUMER_NAME = "payment-service-local";

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(
            PaymentService paymentService,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.created", groupId = CONSUMER_NAME)
    @Transactional
    public void onPaymentCreated(String payload) throws Exception {
        PaymentCreatedPayload event = objectMapper.readValue(payload, PaymentCreatedPayload.class);

        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
            log.info("Event {} already processed by {}, skipping (redelivery)", event.eventId(), CONSUMER_NAME);
            return;
        }

        log.info("Processing payment.created for payment {}", event.paymentId());
        paymentService.transition(event.paymentId(), PaymentStatus.PENDING);
        processedEventRepository.save(new ProcessedEvent(event.eventId(), CONSUMER_NAME));
    }
}
