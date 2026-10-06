package com.paymentorchestrator.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    /**
     * Locks and returns the next batch of unpublished events, oldest first.
     *
     * FOR UPDATE SKIP LOCKED is what makes this safe to run from more than
     * one OutboxPublisher instance at once (e.g. if payment-service is
     * scaled out): each instance locks a disjoint set of rows instead of
     * blocking on - or double-publishing - the same ones. The lock is held
     * for the duration of the caller's transaction (see
     * OutboxPublisher#publishPendingEvents), which both publishes to Kafka
     * and marks the rows PUBLISHED before committing.
     */
    @Query(value = "SELECT * FROM outbox_events " +
            "WHERE status = 'PENDING' " +
            "ORDER BY created_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockNextBatchForPublishing(@Param("limit") int limit);
}
