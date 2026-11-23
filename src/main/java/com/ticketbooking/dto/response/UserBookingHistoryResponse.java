package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UserBookingHistoryResponse(
    Long bookingId,
    String eventTitle,
    String venueName,
    Instant showStartTime,
    List<String> seatLabels,
    BigDecimal totalAmount,
    BookingStatus bookingStatus,
    PaymentStatus paymentStatus,
    Instant createdAt
) {}
