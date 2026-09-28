package com.paymentorchestrator.payment.service;

import com.paymentorchestrator.payment.domain.Payment;
import com.paymentorchestrator.payment.domain.PaymentStatus;
import com.paymentorchestrator.payment.dto.CreatePaymentRequest;
import com.paymentorchestrator.payment.dto.PaymentResponse;
import com.paymentorchestrator.payment.exception.IdempotencyConflictException;
import com.paymentorchestrator.payment.exception.PaymentNotFoundException;
import com.paymentorchestrator.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a payment, or - if {@code idempotencyKey} has been used before -
     * returns the existing payment instead of creating a duplicate.
     *
     * Order of checks matters here:
     *  1. Redis lock: cheap, fast rejection of an obvious concurrent duplicate.
     *  2. DB lookup by idempotency_key: catches a replay of a *completed*
     *     request (the common case - client retried after a timeout even
     *     though the first attempt actually succeeded).
     *  3. DB unique constraint as a safety net: if two requests somehow both
     *     get past 1 and 2 (e.g. the Redis lock expired mid-request), the
     *     database itself refuses the second INSERT. We catch that and
     *     treat it as a replay rather than letting it surface as a 500.
     */
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PaymentResponse.of(existing.get(), true);
        }

        String lockToken = idempotencyService.tryAcquireLock(idempotencyKey);
        if (lockToken == null) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(payment -> PaymentResponse.of(payment, true))
                    .orElseThrow(() -> new IdempotencyConflictException(idempotencyKey));
        }

        try {
            return PaymentResponse.of(persistNewPayment(request, idempotencyKey), false);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(payment -> PaymentResponse.of(payment, true))
                    .orElseThrow(() -> e);
        } finally {
            idempotencyService.releaseLock(idempotencyKey, lockToken);
        }
    }

    @Transactional
    protected Payment persistNewPayment(CreatePaymentRequest request, String idempotencyKey) {
        Payment payment = new Payment(idempotencyKey, request.merchantId(), request.amount(), request.currency());
        // No async pipeline yet (that's week 2+), but conceptually creating
        // a payment means it has been accepted for processing - so we move
        // it straight to PENDING here rather than leaving it sitting in
        // CREATED. Once the Kafka pipeline exists, this transition moves
        // to the outbox-driven flow instead of happening inline.
        payment.transitionTo(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.of(payment, false);
    }

    /**
     * Manually drives a payment to a new status. This endpoint exists only
     * because the async PSP pipeline doesn't exist yet - it's how we exercise
     * and demo the state machine's transition rules before Kafka/PSP gateway
     * land. It will very likely go away (or become an admin-only, audited
     * operation) once real transitions are driven by PSP callbacks.
     */
    @Transactional
    public PaymentResponse transition(UUID id, PaymentStatus targetStatus) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        payment.transitionTo(targetStatus);
        return PaymentResponse.of(payment, false);
    }
}
