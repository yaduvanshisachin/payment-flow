package com.paymentorchestrator.payment.service;

import com.paymentorchestrator.payment.domain.Payment;
import com.paymentorchestrator.payment.domain.PaymentStatus;
import com.paymentorchestrator.payment.dto.CreatePaymentRequest;
import com.paymentorchestrator.payment.dto.PaymentResponse;
import com.paymentorchestrator.payment.exception.IdempotencyConflictException;
import com.paymentorchestrator.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These tests exercise PaymentService's idempotency logic in isolation
 * (repository and Redis lock are mocked) so they run fast and don't need a
 * real Postgres/Redis - that's what a later integration test with
 * Testcontainers would be for. The point here is to pin down the three
 * scenarios idempotency actually has to handle:
 *   1. Brand new key -> creates a payment.
 *   2. Key already has a persisted payment -> replay, no new row.
 *   3. Key is currently locked by another in-flight request, with no
 *      persisted result yet -> conflict, not a silent duplicate.
 */
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentService paymentService;

    private static final CreatePaymentRequest REQUEST =
            new CreatePaymentRequest("merchant-1", new BigDecimal("250.00"), "INR");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentService = new PaymentService(paymentRepository, idempotencyService);
    }

    @Test
    void createsNewPaymentWhenIdempotencyKeyIsUnused() {
        String key = "idem-new";
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquireLock(key)).thenReturn("token-1");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.createPayment(REQUEST, key);

        assertThat(response.replayed()).isFalse();
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(idempotencyService).releaseLock(key, "token-1");
    }

    @Test
    void returnsExistingPaymentWithoutCreatingWhenKeyAlreadyPersisted() {
        String key = "idem-existing";
        Payment existing = new Payment(key, "merchant-1", new BigDecimal("250.00"), "INR");
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(REQUEST, key);

        assertThat(response.replayed()).isTrue();
        verify(paymentRepository, never()).save(any());
        verify(idempotencyService, never()).tryAcquireLock(anyString());
    }

    @Test
    void raceLoserFallsBackToReplayWhenDbConstraintRejectsDuplicateInsert() {
        String key = "idem-race";
        Payment winnerRow = new Payment(key, "merchant-1", new BigDecimal("250.00"), "INR");

        // First lookup (before acquiring the lock): nothing yet.
        // Second lookup (after the DB unique constraint rejects our insert):
        // the other request's row is now visible.
        when(paymentRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerRow));
        when(idempotencyService.tryAcquireLock(key)).thenReturn("token-2");
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        PaymentResponse response = paymentService.createPayment(REQUEST, key);

        assertThat(response.replayed()).isTrue();
        verify(idempotencyService).releaseLock(key, "token-2");
    }

    @Test
    void rejectsWithConflictWhenLockHeldByAnotherRequestAndNoResultYet() {
        String key = "idem-in-flight";
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquireLock(key)).thenReturn(null); // lock held elsewhere

        assertThatThrownBy(() -> paymentService.createPayment(REQUEST, key))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(paymentRepository, never()).save(any());
    }
}
