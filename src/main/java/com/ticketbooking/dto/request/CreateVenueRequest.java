package com.ticketbooking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateVenueRequest(
    @NotBlank(message = "Venue name is required")
    String name,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "Address is required")
    String address,

    @NotNull(message = "totalRows is required")
    @Min(value = 1, message = "totalRows must be at least 1")
    Integer totalRows,

    @NotNull(message = "seatsPerRow is required")
    @Min(value = 1, message = "seatsPerRow must be at least 1")
    Integer seatsPerRow
) {}
