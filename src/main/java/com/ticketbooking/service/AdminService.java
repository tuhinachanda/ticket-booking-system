package com.ticketbooking.service;

import com.ticketbooking.dto.request.CreateEventRequest;
import com.ticketbooking.dto.request.CreateShowRequest;
import com.ticketbooking.dto.request.CreateVenueRequest;
import com.ticketbooking.dto.response.AdminStatsResponse;
import com.ticketbooking.dto.response.EventResponse;
import com.ticketbooking.dto.response.ShowOccupancyStat;
import com.ticketbooking.dto.response.ShowResponse;
import com.ticketbooking.dto.response.VenueResponse;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.Venue;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.model.enums.ShowStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;

    public AdminService(
        VenueRepository venueRepository,
        EventRepository eventRepository,
        ShowRepository showRepository,
        ShowSeatRepository showSeatRepository,
        BookingRepository bookingRepository
    ) {
        this.venueRepository = venueRepository;
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = Venue.builder()
            .name(request.name().trim())
            .city(request.city().trim())
            .address(request.address().trim())
            .totalRows(request.totalRows())
            .seatsPerRow(request.seatsPerRow())
            .build();

        Venue saved = venueRepository.save(venue);
        return new VenueResponse(
            saved.getId(),
            saved.getName(),
            saved.getCity(),
            saved.getAddress(),
            saved.getTotalRows(),
            saved.getSeatsPerRow(),
            saved.getTotalRows() * saved.getSeatsPerRow()
        );
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = Event.builder()
            .title(request.title().trim())
            .description(request.description() != null ? request.description().trim() : null)
            .category(request.category().trim())
            .durationMinutes(request.durationMinutes())
            .posterUrl(request.posterUrl() != null ? request.posterUrl().trim() : null)
            .build();

        Event saved = eventRepository.save(event);
        return new EventResponse(
            saved.getId(),
            saved.getTitle(),
            saved.getDescription(),
            saved.getCategory(),
            saved.getDurationMinutes(),
            saved.getPosterUrl(),
            saved.getCreatedAt()
        );
    }

    /**
     * Creates a scheduled show and automatically generates all ShowSeat rows based on the venue's
     * totalRows and seatsPerRow dimensions.
     */
    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        Event event = eventRepository.findById(request.eventId())
            .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.eventId()));

        Venue venue = venueRepository.findById(request.venueId())
            .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + request.venueId()));

        ShowStatus status = request.status() != null ? request.status() : ShowStatus.SCHEDULED;

        Show show = Show.builder()
            .event(event)
            .venue(venue)
            .startTime(request.startTime())
            .basePrice(request.basePrice())
            .status(status)
            .build();

        Show savedShow = showRepository.save(show);

        // Automatically populate ShowSeat records for the show
        List<ShowSeat> seats = new ArrayList<>();
        for (int r = 0; r < venue.getTotalRows(); r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            for (int s = 1; s <= venue.getSeatsPerRow(); s++) {
                ShowSeat seat = ShowSeat.builder()
                    .show(savedShow)
                    .seatRow(rowLabel)
                    .seatNumber(s)
                    .price(savedShow.getBasePrice())
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build();
                seats.add(seat);
            }
        }
        showSeatRepository.saveAll(seats);

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
            savedShow.getId(),
            event.getId(),
            event.getTitle(),
            venueResponse,
            savedShow.getStartTime(),
            savedShow.getBasePrice(),
            savedShow.getStatus(),
            seats.size(),
            seats.size()
        );
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getAdminStats() {
        long totalBookings = bookingRepository.count();
        long confirmedBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long cancelledBookings = bookingRepository.countByStatus(BookingStatus.CANCELLED);
        BigDecimal totalConfirmedRevenue = bookingRepository.sumConfirmedRevenue();

        List<Show> allShows = showRepository.findAll();
        List<ShowOccupancyStat> showStats = new ArrayList<>();

        for (Show show : allShows) {
            long totalSeats = showSeatRepository.countByShowId(show.getId());
            long bookedSeats = showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.BOOKED);
            double occupancy = totalSeats > 0 ? ((double) bookedSeats / totalSeats) * 100.0 : 0.0;
            BigDecimal revenue = bookingRepository.sumConfirmedRevenueByShowId(show.getId());

            showStats.add(new ShowOccupancyStat(
                show.getId(),
                show.getEvent().getTitle(),
                show.getVenue().getName(),
                show.getStartTime(),
                totalSeats,
                bookedSeats,
                Math.round(occupancy * 100.0) / 100.0,
                revenue
            ));
        }

        return new AdminStatsResponse(
            totalBookings,
            confirmedBookings,
            cancelledBookings,
            totalConfirmedRevenue,
            showStats
        );
    }
}
