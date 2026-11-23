package com.ticketbooking.service;

import com.ticketbooking.dto.response.SeatResponse;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.dto.response.VenueResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.Venue;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowService(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    /**
     * Retrieves the complete seat grid for a show with effective seat statuses.
     *
     * Hold Expiration Rule:
     * If a seat in the database is in status HELD but its lease timestamp `holdExpiresAt` is before
     * `Instant.now()`, its effective status is returned as AVAILABLE so expired holds never appear
     * blocked to end users browsing the seating map.
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> getShowSeats(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show not found with id: " + showId);
        }

        List<ShowSeat> seats = showSeatRepository.findByShowIdOrderBySeatRowAscSeatNumberAsc(showId);
        Instant now = Instant.now();

        return seats.stream().map(seat -> {
            SeatStatus effectiveStatus = seat.getStatus();

            // Evaluate dynamic seat availability against expiration timestamp
            if (effectiveStatus == SeatStatus.HELD &&
                seat.getHoldExpiresAt() != null &&
                seat.getHoldExpiresAt().isBefore(now)) {
                effectiveStatus = SeatStatus.AVAILABLE;
            }

            return new SeatResponse(
                seat.getId(),
                seat.getSeatRow(),
                seat.getSeatNumber(),
                seat.getPrice(),
                effectiveStatus
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public ShowResponse getShowById(Long showId) {
        Show show = showRepository.findByIdWithVenueAndEvent(showId)
            .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        Instant now = Instant.now();
        long availableSeats = showSeatRepository.countAvailableSeats(show.getId(), now);
        long totalSeats = showSeatRepository.countByShowId(show.getId());

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
