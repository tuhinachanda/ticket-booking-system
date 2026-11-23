package com.ticketbooking;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.dto.request.SimulatePaymentRequest;
import com.ticketbooking.dto.response.BookingResponse;
import com.ticketbooking.dto.response.PaymentResponse;
import com.ticketbooking.dto.response.UserBookingHistoryResponse;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.Payment;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.PaymentStatus;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.PaymentService;
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
class PaymentAndCancellationIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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

        // Reset seats to AVAILABLE
        for (ShowSeat seat : testSeats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setHeldByUser(null);
        }
        testSeats = showSeatRepository.saveAll(testSeats);
    }

    @Test
    @DisplayName("Simulate payment SUCCESS marks Booking CONFIRMED and seats permanently BOOKED")
    void testPaymentSimulation_Success() {
        Long seatId = testSeats.get(0).getId();

        // 1. Create booking
        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        // 2. Simulate payment success
        SimulatePaymentRequest payReq = new SimulatePaymentRequest(booking.id(), true);
        PaymentResponse payResponse = paymentService.simulatePayment(testUser.getEmail(), payReq);

        assertThat(payResponse.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payResponse.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(payResponse.transactionRef()).startsWith("TXN-");

        // Verify database state
        Booking confirmedBooking = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(confirmedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        ShowSeat bookedSeat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(bookedSeat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(bookedSeat.getHoldExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Simulate payment FAILURE marks Booking CANCELLED and releases seats to AVAILABLE")
    void testPaymentSimulation_Failure() {
        Long seatId = testSeats.get(0).getId();

        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        SimulatePaymentRequest payReq = new SimulatePaymentRequest(booking.id(), false);
        PaymentResponse payResponse = paymentService.simulatePayment(testUser.getEmail(), payReq);

        assertThat(payResponse.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payResponse.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);

        Booking cancelledBooking = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        ShowSeat releasedSeat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(releasedSeat.getHoldExpiresAt()).isNull();
        assertThat(releasedSeat.getHeldByUser()).isNull();
    }

    @Test
    @DisplayName("Simulate payment rejects expired hold leases with SeatUnavailableException")
    void testPaymentSimulation_RejectsExpiredHold() {
        Long seatId = testSeats.get(0).getId();

        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        // Expire hold
        ShowSeat seat = showSeatRepository.findById(seatId).orElseThrow();
        seat.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        showSeatRepository.save(seat);

        SimulatePaymentRequest payReq = new SimulatePaymentRequest(booking.id(), true);
        assertThatThrownBy(() -> paymentService.simulatePayment(testUser.getEmail(), payReq))
            .isInstanceOf(SeatUnavailableException.class)
            .hasMessageContaining("Hold lease has expired");
    }

    @Test
    @DisplayName("User booking history returns bookings with event title, venue name, seat labels, and statuses")
    void testUserBookingHistory() {
        Long seatId = testSeats.get(0).getId();

        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        SimulatePaymentRequest payReq = new SimulatePaymentRequest(booking.id(), true);
        paymentService.simulatePayment(testUser.getEmail(), payReq);

        List<UserBookingHistoryResponse> history = bookingService.getUserBookingHistory(testUser.getEmail());

        assertThat(history).isNotEmpty();
        UserBookingHistoryResponse item = history.stream()
            .filter(h -> h.bookingId().equals(booking.id()))
            .findFirst()
            .orElseThrow();

        assertThat(item.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(item.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(item.eventTitle()).isNotBlank();
        assertThat(item.venueName()).isNotBlank();
        assertThat(item.seatLabels()).isNotEmpty();
    }

    @Test
    @DisplayName("Cancel booking succeeds when show is > 2h away, marks Payment REFUNDED, and releases seats")
    void testCancelBooking_SuccessWithRefund() {
        Long seatId = testSeats.get(0).getId();

        // 1. Book & pay
        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        SimulatePaymentRequest payReq = new SimulatePaymentRequest(booking.id(), true);
        paymentService.simulatePayment(testUser.getEmail(), payReq);

        // 2. Cancel booking (testShow is scheduled 2+ days away in seed data)
        BookingResponse cancelled = bookingService.cancelBooking(testUser.getEmail(), booking.id());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);

        // Verify seats released
        ShowSeat releasedSeat = showSeatRepository.findById(seatId).orElseThrow();
        assertThat(releasedSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // Verify payment refunded
        Payment payment = paymentRepository.findByBookingId(booking.id()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Cancel booking fails if show start time is within 2 hours")
    void testCancelBooking_FailsWithin2Hours() {
        // Move show start time to 1 hour in the future
        testShow.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS));
        showRepository.save(testShow);

        Long seatId = testSeats.get(0).getId();
        CreateBookingRequest bookingReq = new CreateBookingRequest(testShow.getId(), List.of(seatId));
        BookingResponse booking = bookingService.createBooking(testUser.getEmail(), bookingReq, UUID.randomUUID().toString());

        assertThatThrownBy(() -> bookingService.cancelBooking(testUser.getEmail(), booking.id()))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("within 2 hours of show start time");
    }
}
