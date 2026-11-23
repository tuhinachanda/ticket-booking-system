package com.ticketbooking.dto.response;

import java.math.BigDecimal;

public record BookingSeatResponse(
    Long showSeatId,
    String seatRow,
    Integer seatNumber,
    BigDecimal price
) {}
