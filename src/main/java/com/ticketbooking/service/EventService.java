package com.ticketbooking.service;

import com.ticketbooking.dto.response.EventResponse;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.dto.response.VenueResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.Venue;
import com.ticketbooking.model.enums.ShowStatus;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public EventService(
        EventRepository eventRepository,
        ShowRepository showRepository,
        ShowSeatRepository showSeatRepository
    ) {
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> searchEvents(String city, String category, String search, Pageable pageable) {
        String cleanCity = StringUtils.hasText(city) ? city.trim() : null;
        String cleanCategory = StringUtils.hasText(category) ? category.trim() : null;
        String cleanSearch = StringUtils.hasText(search) ? search.trim() : null;

        Page<Event> eventPage = eventRepository.searchEvents(cleanCity, cleanCategory, cleanSearch, pageable);
        return eventPage.map(this::mapToEventResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        return mapToEventResponse(event);
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> getShowsForEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }

        List<Show> scheduledShows = showRepository.findByEventIdAndStatusWithVenue(eventId, ShowStatus.SCHEDULED);
        Instant now = Instant.now();

        return scheduledShows.stream().map(show -> {
            // Count dynamically considers AVAILABLE seats plus expired HELD leases as available
            long availableSeats = showSeatRepository.countAvailableSeats(show.getId(), now);
            long totalSeats = showSeatRepository.countByShowId(show.getId());
            return mapToShowResponse(show, availableSeats, totalSeats);
        }).toList();
    }

    private EventResponse mapToEventResponse(Event event) {
        return new EventResponse(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getDurationMinutes(),
            event.getPosterUrl(),
            event.getCreatedAt()
        );
    }

    private ShowResponse mapToShowResponse(Show show, long availableSeats, long totalSeats) {
        Venue venue = show.getVenue();
        VenueResponse venueResponse = new VenueResponse(
            venue.getId(),
            venue.getName(),
            venue.getCity(),
            venue.getAddress(),
            venue.getTotalRows(),
            venue.getSeatsPerRow(),
            venue.getTotalRows() * venue.getSeatsPerRow()
        );

        return new ShowResponse(
            show.getId(),
            show.getEvent().getId(),
            show.getEvent().getTitle(),
            venueResponse,
            show.getStartTime(),
            show.getBasePrice(),
            show.getStatus(),
            availableSeats,
            totalSeats
        );
    }
}
