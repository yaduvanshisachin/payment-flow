package com.paymentorchestrator.payment.controller;

import com.paymentorchestrator.payment.dto.CreatePaymentRequest;
import com.paymentorchestrator.payment.dto.PaymentResponse;
import com.paymentorchestrator.payment.dto.TransitionRequest;
import com.paymentorchestrator.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Creates a payment. Requires an Idempotency-Key header; sending the
     * same key twice returns the original payment instead of creating a
     * second one (see PaymentService#createPayment for how that's enforced).
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        if (response.replayed()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/payments/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * TEMPORARY: manually drives a state transition. This stands in for the
     * async PSP-callback-driven transitions that land in later weeks - it
     * lets us test and demo the state machine's transition rules right now.
     */
    @PostMapping("/{id}/transitions")
    public ResponseEntity<PaymentResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.transition(id, request.targetStatus()));
    }
}
