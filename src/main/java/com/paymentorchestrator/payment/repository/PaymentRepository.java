package com.paymentorchestrator.payment.repository;

import com.paymentorchestrator.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * The durable half of idempotency: the DB-level unique constraint on
     * idempotency_key is what actually prevents two payments with the same
     * key existing, even if the Redis lock in IdempotencyService is bypassed,
     * expires, or a race slips through it.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
