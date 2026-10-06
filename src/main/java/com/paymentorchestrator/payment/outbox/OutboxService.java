package com.paymentorchestrator.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The only supported way to write an outbox event.
 *
 * Propagation.MANDATORY is deliberate, not a default: it makes it impossible
 * to call record() outside of an existing transaction. If you call this from
 * code that isn't already @Transactional, it throws immediately instead of
 * silently writing the outbox row in its own transaction - which would
 * reopen exactly the bug the outbox pattern exists to close (business write
 * succeeds, event write fails separately, or vice versa).
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for event type " + eventType, e);
        }
        outboxEventRepository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
    }
}
