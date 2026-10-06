package com.paymentorchestrator.payment.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
