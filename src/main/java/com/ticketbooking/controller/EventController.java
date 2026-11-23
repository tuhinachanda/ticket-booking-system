package com.ticketbooking.controller;

import com.ticketbooking.dto.response.EventResponse;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Public endpoints for browsing events and scheduled shows")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(
        summary = "Search events",
        description = "Retrieves a paginated list of events filtered optionally by city, category, or search keywords."
    )
    public ResponseEntity<Page<EventResponse>> getEvents(
        @Parameter(description = "Filter by venue city") @RequestParam(required = false) String city,
        @Parameter(description = "Filter by event category") @RequestParam(required = false) String category,
        @Parameter(description = "Search in title and description") @RequestParam(required = false) String search,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<EventResponse> response = eventService.searchEvents(city, category, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieves details of a single event by its unique ID.")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        EventResponse response = eventService.getEventById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventId}/shows")
    @Operation(
        summary = "Get scheduled shows for an event",
        description = "Retrieves all upcoming scheduled shows for an event with venue info and available seat counts."
    )
    public ResponseEntity<List<ShowResponse>> getShowsForEvent(@PathVariable Long eventId) {
        List<ShowResponse> response = eventService.getShowsForEvent(eventId);
        return ResponseEntity.ok(response);
    }
}
