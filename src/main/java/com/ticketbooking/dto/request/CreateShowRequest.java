package com.ticketbooking.dto.request;

import com.ticketbooking.model.enums.ShowStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateShowRequest(
    @NotNull(message = "eventId is required")
    Long eventId,

    @NotNull(message = "venueId is required")
    Long venueId,

    @NotNull(message = "startTime is required")
    Instant startTime,

    @NotNull(message = "basePrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "basePrice must be greater than zero")
    BigDecimal basePrice,

    ShowStatus status
) {}
