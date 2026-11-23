package com.ticketbooking.data;

import com.ticketbooking.model.Event;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.Venue;
import com.ticketbooking.model.enums.Role;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.model.enums.ShowStatus;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.repository.VenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
        UserRepository userRepository,
        VenueRepository venueRepository,
        EventRepository eventRepository,
        ShowRepository showRepository,
        ShowSeatRepository showSeatRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping DataSeeder.");
            return;
        }

        log.info("Starting database seeding for initial demo data...");

        // 1. Seed Users (1 Admin and 1 Regular Customer)
        User adminUser = User.builder()
            .name("Admin User")
            .email("admin@ticket.com")
            .passwordHash(passwordEncoder.encode("Admin@123"))
            .role(Role.ADMIN)
            .build();

        User regularUser = User.builder()
            .name("Regular User")
            .email("user@ticket.com")
            .passwordHash(passwordEncoder.encode("User@123"))
            .role(Role.USER)
            .build();

        userRepository.saveAll(List.of(adminUser, regularUser));

        // 2. Seed 2 Venues
        Venue venue1 = Venue.builder()
            .name("City Center Hall")
            .city("Chicago")
            .address("100 North Michigan Avenue")
            .totalRows(5)
            .seatsPerRow(10)
            .build();

        Venue venue2 = Venue.builder()
            .name("Riverside Theater")
            .city("Boston")
            .address("200 Commonwealth Avenue")
            .totalRows(4)
            .seatsPerRow(8)
            .build();

        List<Venue> venues = venueRepository.saveAll(List.of(venue1, venue2));

        // 3. Seed 3 Events
        Event event1 = Event.builder()
            .title("Chamber Music Evening")
            .description("An acoustic musical performance with classical strings.")
            .category("Music")
            .durationMinutes(120)
            .posterUrl("https://example.com/posters/music.png")
            .build();

        Event event2 = Event.builder()
            .title("Short Film Showcase")
            .description("A curated screening of selected short films.")
            .category("Cinema")
            .durationMinutes(110)
            .posterUrl("https://example.com/posters/cinema.png")
            .build();

        Event event3 = Event.builder()
            .title("Standup Comedy Night")
            .description("A live standup comedy performance.")
            .category("Comedy")
            .durationMinutes(90)
            .posterUrl("https://example.com/posters/comedy.png")
            .build();

        List<Event> events = eventRepository.saveAll(List.of(event1, event2, event3));

        // 4. Seed Upcoming Shows
        Instant baseTime = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        Show show1 = Show.builder()
            .event(events.get(0))
            .venue(venues.get(0))
            .startTime(baseTime)
            .basePrice(new BigDecimal("50.00"))
            .status(ShowStatus.SCHEDULED)
            .build();

        Show show2 = Show.builder()
            .event(events.get(1))
            .venue(venues.get(1))
            .startTime(baseTime.plus(1, ChronoUnit.DAYS))
            .basePrice(new BigDecimal("20.00"))
            .status(ShowStatus.SCHEDULED)
            .build();

        Show show3 = Show.builder()
            .event(events.get(2))
            .venue(venues.get(0))
            .startTime(baseTime.plus(2, ChronoUnit.DAYS))
            .basePrice(new BigDecimal("35.00"))
            .status(ShowStatus.SCHEDULED)
            .build();

        List<Show> shows = showRepository.saveAll(List.of(show1, show2, show3));

        // 5. Populate ShowSeat rows for each show based on venue capacity
        List<ShowSeat> allSeats = new ArrayList<>();
        for (Show show : shows) {
            Venue venue = show.getVenue();
            for (int r = 0; r < venue.getTotalRows(); r++) {
                String rowLabel = String.valueOf((char) ('A' + r));
                for (int s = 1; s <= venue.getSeatsPerRow(); s++) {
                    ShowSeat seat = ShowSeat.builder()
                        .show(show)
                        .seatRow(rowLabel)
                        .seatNumber(s)
                        .price(show.getBasePrice())
                        .status(SeatStatus.AVAILABLE)
                        .version(0L)
                        .build();
                    allSeats.add(seat);
                }
            }
        }

        showSeatRepository.saveAll(allSeats);

        log.info("Database seeding successfully completed: 2 users, 2 venues, 3 events, {} shows, {} seats.",
            shows.size(), allSeats.size());
    }
}
