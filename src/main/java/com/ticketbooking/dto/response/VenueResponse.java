package com.ticketbooking.dto.response;

public record VenueResponse(
    Long id,
    String name,
    String city,
    String address,
    Integer totalRows,
    Integer seatsPerRow,
    Integer totalCapacity
) {}
