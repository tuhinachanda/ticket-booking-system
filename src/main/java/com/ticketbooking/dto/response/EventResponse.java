package com.ticketbooking.dto.response;

import java.time.Instant;

public record EventResponse(
    Long id,
    String title,
    String description,
    String category,
    Integer durationMinutes,
    String posterUrl,
    Instant createdAt
) {}
