package com.ticketbooking.dto.response;

import com.ticketbooking.model.enums.Role;

public record AuthResponse(
    String token,
    Long userId,
    String name,
    String email,
    Role role
) {}
