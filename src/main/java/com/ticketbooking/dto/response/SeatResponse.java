package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.SeatStatus;

import java.math.BigDecimal;

public record SeatResponse(
    Long id,
    String seatRow,
    Integer seatNumber,
    BigDecimal price,
    SeatStatus effectiveStatus
) {}
