package com.ticketbooking;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.dto.response.BookingResponse;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.BookingSeat;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.Payment;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.Venue;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.PaymentStatus;
import com.ticketbooking.model.enums.Role;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.model.enums.ShowStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceUnitTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShowSeatRepository showSeatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Venue venue;
    private Event event;
    private Show show;
    private ShowSeat seat;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(1L)
            .name("Test User")
            .email("test@example.com")
            .role(Role.USER)
            .build();

        venue = Venue.builder()
            .id(10L)
            .name("Test Venue")
            .city("Test City")
            .address("Test Address")
            .totalRows(5)
            .seatsPerRow(10)
            .build();

        event = Event.builder()
            .id(20L)
            .title("Test Event")
            .category("Music")
            .durationMinutes(120)
            .build();

        show = Show.builder()
            .id(100L)
            .event(event)
            .venue(venue)
            .startTime(Instant.now().plus(3, ChronoUnit.DAYS))
            .basePrice(new BigDecimal("50.00"))
            .status(ShowStatus.SCHEDULED)
            .build();

        seat = ShowSeat.builder()
            .id(500L)
            .show(show)
            .seatRow("A")
            .seatNumber(1)
            .price(new BigDecimal("50.00"))
            .status(SeatStatus.AVAILABLE)
            .version(0L)
            .build();
    }

    @Test
    @DisplayName("Edge Case: Expired hold takeover successfully re-allocates seat to new requester")
    void testCreateBooking_ExpiredHoldTakeover() {
        User previousHolder = User.builder().id(2L).email("prev@example.com").build();
        seat.setStatus(SeatStatus.HELD);
        seat.setHoldExpiresAt(Instant.now().minus(2, ChronoUnit.MINUTES)); // Expired lease
        seat.setHeldByUser(previousHolder);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(showRepository.existsById(100L)).thenReturn(true);
        when(showSeatRepository.findByIdInOrderByIdAscWithLock(List.of(500L))).thenReturn(List.of(seat));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(999L);
            return b;
        });

        CreateBookingRequest request = new CreateBookingRequest(100L, List.of(500L));
        BookingResponse response = bookingService.createBooking("test@example.com", request, null);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(999L);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldByUser()).isEqualTo(user);
        assertThat(seat.getHoldExpiresAt()).isAfter(Instant.now());

        verify(showSeatRepository).saveAll(List.of(seat));
    }

    @Test
    @DisplayName("Edge Case: Idempotency replay returns cached booking without re-locking seats")
    void testCreateBooking_IdempotencyReplay() {
        String idempotencyKey = "idemp-key-12345";

        Booking existingBooking = Booking.builder()
            .id(888L)
            .user(user)
            .show(show)
            .idempotencyKey(idempotencyKey)
            .totalAmount(new BigDecimal("50.00"))
            .status(BookingStatus.PENDING)
            .bookingSeats(new ArrayList<>(List.of(
                BookingSeat.builder().showSeat(seat).priceAtBooking(new BigDecimal("50.00")).build()
            )))
            .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bookingRepository.findByIdempotencyKeyAndUserId(idempotencyKey, user.getId()))
            .thenReturn(Optional.of(existingBooking));

        CreateBookingRequest request = new CreateBookingRequest(100L, List.of(500L));
        BookingResponse response = bookingService.createBooking("test@example.com", request, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(888L);
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);

        // Verify seats were never locked or re-fetched from database
        verify(showSeatRepository, never()).findByIdInOrderByIdAscWithLock(anyList());
    }

    @Test
    @DisplayName("Edge Case: Cancellation fails when show start time is within 2-hour window")
    void testCancelBooking_FailsWithin2HourWindow() {
        show.setStartTime(Instant.now().plus(90, ChronoUnit.MINUTES)); // 1.5 hours away

        Booking booking = Booking.builder()
            .id(777L)
            .user(user)
            .show(show)
            .status(BookingStatus.CONFIRMED)
            .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bookingRepository.findByIdWithLock(777L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking("test@example.com", 777L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("within 2 hours of show start time");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("Edge Case: Cancellation succeeds when show is > 2h away, refunds payment and frees seats")
    void testCancelBooking_SucceedsWhenMoreThan2Hours() {
        show.setStartTime(Instant.now().plus(5, ChronoUnit.HOURS)); // 5 hours away

        seat.setStatus(SeatStatus.BOOKED);
        seat.setHeldByUser(user);

        Booking booking = Booking.builder()
            .id(666L)
            .user(user)
            .show(show)
            .status(BookingStatus.CONFIRMED)
            .bookingSeats(new ArrayList<>(List.of(
                BookingSeat.builder().showSeat(seat).priceAtBooking(new BigDecimal("50.00")).build()
            )))
            .build();

        Payment payment = Payment.builder()
            .id(111L)
            .booking(booking)
            .amount(new BigDecimal("50.00"))
            .status(PaymentStatus.SUCCEEDED)
            .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bookingRepository.findByIdWithLock(666L)).thenReturn(Optional.of(booking));
        when(showSeatRepository.findByIdInOrderByIdAscWithLock(List.of(500L))).thenReturn(List.of(seat));
        when(paymentRepository.findByBookingId(666L)).thenReturn(Optional.of(payment));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        BookingResponse response = bookingService.cancelBooking("test@example.com", 666L);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHoldExpiresAt()).isNull();
        assertThat(seat.getHeldByUser()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        verify(paymentRepository).save(payment);
        verify(showSeatRepository).saveAll(List.of(seat));
    }
}
