package com.ticketbooking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowOccupancyStat(
    Long showId,
    String eventTitle,
    String venueName,
    Instant startTime,
    long totalSeats,
    long bookedSeats,
    double occupancyPercentage,
    BigDecimal revenue
) {}
