package com.ticketbooking.controller;

import com.ticketbooking.dto.response.SeatResponse;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@Tag(name = "Shows", description = "Public endpoints for inspecting show details and seat availability maps")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/{showId}")
    @Operation(summary = "Get show by ID", description = "Retrieves details of a show including venue and seat counts.")
    public ResponseEntity<ShowResponse> getShowById(@PathVariable Long showId) {
        ShowResponse response = showService.getShowById(showId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{showId}/seats")
    @Operation(
        summary = "Get seat map for a show",
        description = "Retrieves the full seat grid with dynamic effective statuses (expired holds resolve to AVAILABLE)."
    )
    public ResponseEntity<List<SeatResponse>> getShowSeats(@PathVariable Long showId) {
        List<SeatResponse> response = showService.getShowSeats(showId);
        return ResponseEntity.ok(response);
    }
}
