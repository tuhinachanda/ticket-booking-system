package com.ticketbooking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingRequest(
    @NotNull(message = "showId is required")
    Long showId,

    @NotEmpty(message = "seatIds must contain at least one seat ID")
    List<Long> seatIds
) {}
