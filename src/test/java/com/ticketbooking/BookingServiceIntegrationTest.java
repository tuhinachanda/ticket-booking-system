package com.ticketbooking;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.dto.response.BookingResponse;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingCleanupService;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingCleanupService bookingCleanupService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private Show testShow;
    private User testUser;
    private List<ShowSeat> testSeats;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("user@ticket.com").orElseThrow();
        testShow = showRepository.findAll().stream().findFirst().orElseThrow();
        testSeats = showSeatRepository.findByShowId(testShow.getId());

        // Reset seats to AVAILABLE for a clean test baseline
        for (ShowSeat seat : testSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setHeldByUser(null);
        }
        testSeats = showSeatRepository.saveAll(testSeats);
    }

    @Test
    @DisplayName("Should successfully create a booking, transition seats to HELD, and set 5-minute lease")
    void testCreateBooking_Success() {
        Long seatId1 = testSeats.get(0).getId();
        Long seatId2 = testSeats.get(1).getId();

        String idempotencyKey = UUID.randomUUID().toString();
        CreateBookingRequest request = new CreateBookingRequest(testShow.getId(), List.of(seatId2, seatId1));

        BookingResponse response = bookingService.createBooking(testUser.getEmail(), request, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.seats()).hasSize(2);
        assertThat(response.holdExpiresAt()).isAfter(Instant.now().plus(4, ChronoUnit.MINUTES));

        // Verify seats in database are HELD
        ShowSeat updatedSeat1 = showSeatRepository.findById(seatId1).orElseThrow();
        ShowSeat updatedSeat2 = showSeatRepository.findById(seatId2).orElseThrow();

        assertThat(updatedSeat1.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(updatedSeat1.getHeldByUser().getId()).isEqualTo(testUser.getId());
        assertThat(updatedSeat2.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    @DisplayName("Should return existing booking on duplicate Idempotency-Key without re-locking")
    void testCreateBooking_Idempotency() {
        Long seatId = testSeats.get(0).getId();
        String idempotencyKey = "test-idempotency-" + UUID.randomUUID();

        CreateBookingRequest request = new CreateBookingRequest(testShow.getId(), List.of(seatId));

        BookingResponse firstResponse = bookingService.createBooking(testUser.getEmail(), request, idempotencyKey);
        BookingResponse secondResponse = bookingService.createBooking(testUser.getEmail(), request, idempotencyKey);

        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        assertThat(secondResponse.idempotencyKey()).isEqualTo(firstResponse.idempotencyKey());
    }

    @Test
    @DisplayName("Should throw SeatUnavailableException when requesting an actively held seat")
    void testCreateBooking_ConflictWhenAlreadyHeld() {
        Long seatId = testSeats.get(0).getId();

        // User 1 holds the seat
        CreateBookingRequest request1 = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        bookingService.createBooking(testUser.getEmail(), request1, UUID.randomUUID().toString());

        // Admin user tries to hold the same seat while lease is active
        CreateBookingRequest request2 = new CreateBookingRequest(testShow.getId(), List.of(seatId));

        assertThatThrownBy(() -> bookingService.createBooking("admin@ticket.com", request2, UUID.randomUUID().toString()))
            .isInstanceOf(SeatUnavailableException.class)
            .hasMessageContaining("currently held by another user");
    }

    @Test
    @DisplayName("Should allow new booking on seat if previous hold has expired")
    void testCreateBooking_AllowsHoldWhenExpired() {
        ShowSeat seat = showSeatRepository.findById(testSeats.get(0).getId()).orElseThrow();
        seat.setStatus(SeatStatus.HELD);
        seat.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)); // Expired hold
        seat.setHeldByUser(testUser);
        showSeatRepository.save(seat);

        // Admin user should be able to hold the expired seat
        CreateBookingRequest request = new CreateBookingRequest(testShow.getId(), List.of(seat.getId()));
        BookingResponse response = bookingService.createBooking("admin@ticket.com", request, UUID.randomUUID().toString());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);

        ShowSeat updatedSeat = showSeatRepository.findById(seat.getId()).orElseThrow();
        User adminUser = userRepository.findByEmail("admin@ticket.com").orElseThrow();
        assertThat(updatedSeat.getHeldByUser().getId()).isEqualTo(adminUser.getId());
    }

    @Test
    @DisplayName("Scheduled cleanup task should reset expired HELD seats to AVAILABLE and mark bookings EXPIRED")
    void testCleanupExpiredHolds() {
        Long seatId = testSeats.get(0).getId();

        // Create a booking
        CreateBookingRequest request = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), request, UUID.randomUUID().toString());

        // Fast-forward hold expiry to the past
        ShowSeat seat = showSeatRepository.findById(seatId).orElseThrow();
        seat.setHoldExpiresAt(Instant.now().minus(2, ChronoUnit.MINUTES));
        showSeatRepository.save(seat);

        // Run scheduled cleanup
        bookingCleanupService.cleanupExpiredHolds();

        // Verify seat is reset to AVAILABLE
        ShowSeat cleanedSeat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(cleanedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(cleanedSeat.getHoldExpiresAt()).isNull();
        assertThat(cleanedSeat.getHeldByUser()).isNull();

        // Verify booking is marked EXPIRED
        Booking expiredBooking = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }
}
