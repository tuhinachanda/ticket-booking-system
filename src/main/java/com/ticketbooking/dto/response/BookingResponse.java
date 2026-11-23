package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
    Long id,
    Long userId,
    Long showId,
    String showTitle,
    String venueName,
    Instant showStartTime,
    String idempotencyKey,
    BigDecimal totalAmount,
    BookingStatus status,
    Instant holdExpiresAt,
    Instant createdAt,
    List<BookingSeatResponse> seats
) {}
