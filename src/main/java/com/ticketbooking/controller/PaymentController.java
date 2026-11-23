package com.ticketbooking.controller;

import com.ticketbooking.dto.request.SimulatePaymentRequest;
import com.ticketbooking.dto.response.PaymentResponse;
import com.ticketbooking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Endpoints for payment simulation and transaction handling")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/simulate")
    @Operation(
        summary = "Simulate payment processing",
        description = "Processes or fails a payment for a pending booking. On success, marks booking CONFIRMED and seats BOOKED. On failure, cancels booking and releases seats."
    )
    public ResponseEntity<PaymentResponse> simulatePayment(
        @Valid @RequestBody SimulatePaymentRequest request,
        Authentication authentication
    ) {
        String userEmail = authentication.getName();
        PaymentResponse response = paymentService.simulatePayment(userEmail, request);
        return ResponseEntity.ok(response);
    }
}
