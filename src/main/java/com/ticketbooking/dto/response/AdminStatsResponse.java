package com.ticketbooking.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdminStatsResponse(
    long totalBookings,
    long confirmedBookings,
    long cancelledBookings,
    BigDecimal totalConfirmedRevenue,
    List<ShowOccupancyStat> showStats
) {}
