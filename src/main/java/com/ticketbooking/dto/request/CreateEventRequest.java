package com.ticketbooking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
    @NotBlank(message = "Title is required")
    String title,

    String description,

    @NotBlank(message = "Category is required")
    String category,

    @NotNull(message = "durationMinutes is required")
    @Min(value = 1, message = "durationMinutes must be at least 1")
    Integer durationMinutes,

    String posterUrl
) {}
