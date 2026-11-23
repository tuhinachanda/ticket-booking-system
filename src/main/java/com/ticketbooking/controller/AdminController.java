package com.ticketbooking.controller;

import com.ticketbooking.dto.request.CreateEventRequest;
import com.ticketbooking.dto.request.CreateShowRequest;
import com.ticketbooking.dto.request.CreateVenueRequest;
import com.ticketbooking.dto.response.AdminStatsResponse;
import com.ticketbooking.dto.response.EventResponse;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.dto.response.VenueResponse;
import com.ticketbooking.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative endpoints for managing venues, events, shows, and viewing system stats")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/venues")
    @Operation(summary = "Create venue", description = "Creates a new venue with row and seat capacity dimensions.")
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse response = adminService.createVenue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/events")
    @Operation(summary = "Create event", description = "Creates a new event catalog entry.")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = adminService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/shows")
    @Operation(
        summary = "Create show",
        description = "Creates a scheduled show and automatically generates all individual ShowSeat records for the venue capacity."
    )
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        ShowResponse response = adminService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get admin analytics", description = "Returns total bookings, confirmed revenue, and per-show occupancy statistics.")
    public ResponseEntity<AdminStatsResponse> getStats() {
        AdminStatsResponse response = adminService.getAdminStats();
        return ResponseEntity.ok(response);
    }
}
