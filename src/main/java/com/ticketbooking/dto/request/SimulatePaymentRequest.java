package com.ticketbooking.dto.request;

import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(
    @NotNull(message = "bookingId is required")
    Long bookingId,

    @NotNull(message = "simulateSuccess is required")
    Boolean simulateSuccess
) {}
