package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long paymentId,
    Long bookingId,
    BigDecimal amount,
    PaymentStatus paymentStatus,
    BookingStatus bookingStatus,
    String transactionRef,
    Instant createdAt
) {}
