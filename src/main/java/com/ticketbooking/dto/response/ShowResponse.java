package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.ShowStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowResponse(
    Long id,
    Long eventId,
    String eventTitle,
    VenueResponse venue,
    Instant startTime,
    BigDecimal basePrice,
    ShowStatus status,
    long availableSeatsCount,
    long totalSeatsCount
) {}
