package com.ticketbooking.controller;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.dto.response.BookingResponse;
import com.ticketbooking.dto.response.UserBookingHistoryResponse;
import com.ticketbooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Endpoints for reserving seats with double-booking prevention and idempotency")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(
        summary = "Create or hold booking",
        description = "Reserves requested seats under a 5-minute hold lease. Enforces idempotency via Idempotency-Key and prevents deadlocks."
    )
    public ResponseEntity<BookingResponse> createBooking(
        @Parameter(description = "Optional unique idempotency key for idempotent checkouts")
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateBookingRequest request,
        Authentication authentication
    ) {
        String userEmail = authentication.getName();
        BookingResponse response = bookingService.createBooking(userEmail, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user booking history", description = "Retrieves all bookings made by the authenticated user with event, venue, and payment details.")
    public ResponseEntity<List<UserBookingHistoryResponse>> getMyBookings(Authentication authentication) {
        String userEmail = authentication.getName();
        List<UserBookingHistoryResponse> response = bookingService.getUserBookingHistory(userEmail);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking details", description = "Retrieves details of a user's booking by its ID.")
    public ResponseEntity<BookingResponse> getBookingById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        String userEmail = authentication.getName();
        BookingResponse response = bookingService.getBookingById(id, userEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel booking", description = "Cancels a booking if the show start time is at least 2 hours in the future. Refunds payment if completed and releases seats.")
    public ResponseEntity<BookingResponse> cancelBooking(
        @PathVariable Long bookingId,
        Authentication authentication
    ) {
        String userEmail = authentication.getName();
        BookingResponse response = bookingService.cancelBooking(userEmail, bookingId);
        return ResponseEntity.ok(response);
    }
}
