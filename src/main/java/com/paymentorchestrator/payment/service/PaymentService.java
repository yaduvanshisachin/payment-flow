package com.paymentorchestrator.payment.service;

import com.paymentorchestrator.payment.domain.Payment;
import com.paymentorchestrator.payment.domain.PaymentStatus;
import com.paymentorchestrator.payment.dto.CreatePaymentRequest;
import com.paymentorchestrator.payment.dto.PaymentResponse;
import com.paymentorchestrator.payment.event.PaymentCreatedPayload;
import com.paymentorchestrator.payment.exception.IdempotencyConflictException;
import com.paymentorchestrator.payment.exception.PaymentNotFoundException;
import com.paymentorchestrator.payment.outbox.OutboxService;
import com.paymentorchestrator.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService,
                          OutboxService outboxService, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        // persistNewPayment() is called as this.persistNewPayment(...) from
        // createPayment() below - a self-invocation. Spring's @Transactional
        // is implemented via a proxy wrapping this bean, and self-invocation
        // calls the real object directly, bypassing that proxy entirely. An
        // @Transactional annotation on persistNewPayment would therefore be
        // silently ignored - no transaction would actually be open when
        // outboxService.record() runs, and since that method requires one
        // (Propagation.MANDATORY), it would throw at runtime. Using
        // TransactionTemplate programmatically sidesteps the proxy issue
        // entirely by demarcating the transaction explicitly, right here,
        // rather than relying on an annotation that self-invocation defeats.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

    /**
     * Persists the payment in CREATED status and, in the SAME transaction,
     * writes a "payment.created" outbox row (see OutboxService). The payment
     * does NOT jump to PENDING here anymore - that transition now happens
     * asynchronously, driven by PaymentEventConsumer after the event has
     * round-tripped through Kafka. This is deliberate: it's what makes the
     * API handler's job purely "durably record intent" and pushes all
     * actual processing onto the async pipeline, which is the whole point
     * of week 2.
     *
     * See the constructor comment for why this uses TransactionTemplate
     * instead of @Transactional.
     */
    private Payment persistNewPayment(CreatePaymentRequest request, String idempotencyKey) {
        return transactionTemplate.execute(status -> {
            Payment payment = new Payment(idempotencyKey, request.merchantId(), request.amount(), request.currency());
            Payment saved = paymentRepository.save(payment);

            PaymentCreatedPayload payload = new PaymentCreatedPayload(
                    UUID.randomUUID(),
                    saved.getId(),
                    saved.getMerchantId(),
                    saved.getAmount(),
                    saved.getCurrency(),
                    saved.getStatus().name(),
                    Instant.now()
            );
            outboxService.record("PAYMENT", saved.getId(), "payment.created", payload);

            return saved;
        });
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
