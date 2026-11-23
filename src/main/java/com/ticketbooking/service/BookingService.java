package com.ticketbooking.service;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.dto.response.BookingResponse;
import com.ticketbooking.dto.response.BookingSeatResponse;
import com.ticketbooking.dto.response.UserBookingHistoryResponse;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.BookingSeat;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final ShowRepository showRepository;
    private final PaymentRepository paymentRepository;

    public BookingService(
        BookingRepository bookingRepository,
        ShowSeatRepository showSeatRepository,
        UserRepository userRepository,
        ShowRepository showRepository,
        PaymentRepository paymentRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.userRepository = userRepository;
        this.showRepository = showRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Core Double-Booking Prevention & Reservation Engine:
     *
     * 1. Idempotency Check:
     *    If an idempotency key is supplied and already recorded for this user, returns the existing
     *    booking immediately, avoiding duplicate order creations or double charging.
     *
     * 2. Deadlock Prevention Strategy:
     *    Requested seat IDs are sorted in strict ascending order before issuing database lock queries.
     *    When two concurrent requests attempt to reserve overlapping sets of seats (e.g. {1, 2} and {2, 1}),
     *    enforcing global ascending lock acquisition ensures both transactions lock seat 1 before seat 2,
     *    completely eliminating circular-wait deadlocks.
     *
     * 3. Pessimistic Locking with Timeout:
     *    Uses SELECT ... FOR UPDATE with a 3-second timeout hint. Threads fail fast if a lock cannot be
     *    acquired, preventing thread pool exhaustion during peak demand bursts.
     *
     * 4. Seat Availability & Hold Expiration Validation:
     *    A seat is valid for reservation if it is AVAILABLE, OR if it is HELD but its hold lease has expired
     *    (holdExpiresAt < now). If any requested seat is BOOKED or actively held by someone else, a
     *    SeatUnavailableException is thrown (mapped to HTTP 409 Conflict).
     *
     * 5. State Transition & Hold Lease:
     *    Valid seats transition to HELD with a 5-minute expiration lease (now + 5 min), bound to the user,
     *    and a PENDING booking record is created capturing price snapshots.
     */
    @Transactional
    public BookingResponse createBooking(String userEmail, CreateBookingRequest request, String idempotencyKey) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // 1. Idempotency Check
        String effectiveIdempotencyKey = StringUtils.hasText(idempotencyKey)
            ? idempotencyKey.trim()
            : UUID.randomUUID().toString();

        if (StringUtils.hasText(idempotencyKey)) {
            var existingBooking = bookingRepository.findByIdempotencyKeyAndUserId(effectiveIdempotencyKey, user.getId());
            if (existingBooking.isPresent()) {
                return mapToBookingResponse(existingBooking.get());
            }
        }

        if (!showRepository.existsById(request.showId())) {
            throw new ResourceNotFoundException("Show not found with id: " + request.showId());
        }

        // 2. Deadlock Prevention: Sort seat IDs in ascending order before acquiring pessimistic locks
        List<Long> sortedSeatIds = request.seatIds().stream()
            .distinct()
            .sorted()
            .toList();

        if (sortedSeatIds.isEmpty()) {
            throw new BadRequestException("Must select at least one seat");
        }

        // 3. Fetch seats with PESSIMISTIC_WRITE lock and timeout hint
        List<ShowSeat> seats = showSeatRepository.findByIdInOrderByIdAscWithLock(sortedSeatIds);

        if (seats.size() != sortedSeatIds.size()) {
            throw new BadRequestException("One or more requested seat IDs do not exist");
        }

        Instant now = Instant.now();

        // 4. Validate that all seats belong to the target show and are available
        for (ShowSeat seat : seats) {
            if (!seat.getShow().getId().equals(request.showId())) {
                throw new BadRequestException(
                    "Seat " + seat.getSeatRow() + seat.getSeatNumber() + " does not belong to show " + request.showId()
                );
            }

            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new SeatUnavailableException(
                    "Seat " + seat.getSeatRow() + seat.getSeatNumber() + " is already booked"
                );
            }

            // A seat is available if status is AVAILABLE, or if it is HELD but the hold lease has expired
            boolean isAvailable = (seat.getStatus() == SeatStatus.AVAILABLE) ||
                (seat.getStatus() == SeatStatus.HELD && seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now));

            if (!isAvailable) {
                throw new SeatUnavailableException(
                    "Seat " + seat.getSeatRow() + seat.getSeatNumber() + " is currently held by another user"
                );
            }
        }

        // 5. Transition seats to HELD with 5-minute lease
        Instant holdExpiresAt = now.plus(5, ChronoUnit.MINUTES);
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.HELD);
            seat.setHoldExpiresAt(holdExpiresAt);
            seat.setHeldByUser(user);
        }
        showSeatRepository.saveAll(seats);

        // 6. Calculate total amount and create PENDING booking with seat price snapshots
        BigDecimal totalAmount = seats.stream()
            .map(ShowSeat::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Show show = seats.get(0).getShow();

        Booking booking = Booking.builder()
            .user(user)
            .show(show)
            .idempotencyKey(effectiveIdempotencyKey)
            .totalAmount(totalAmount)
            .status(BookingStatus.PENDING)
            .build();

        for (ShowSeat seat : seats) {
            BookingSeat bookingSeat = BookingSeat.builder()
                .booking(booking)
                .showSeat(seat)
                .priceAtBooking(seat.getPrice())
                .build();
            booking.getBookingSeats().add(bookingSeat);
        }

        Booking savedBooking = bookingRepository.save(booking);
        return mapToBookingResponse(savedBooking, holdExpiresAt);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // Allow owner or admin to view
        if (!booking.getUser().getId().equals(user.getId()) && user.getRole() != com.ticketbooking.model.enums.Role.ADMIN) {
            throw new BadRequestException("Access denied to booking: " + bookingId);
        }

        return mapToBookingResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<UserBookingHistoryResponse> getUserBookingHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        List<Booking> bookings = bookingRepository.findHistoryByUserId(user.getId());

        return bookings.stream().map(b -> {
            List<String> seatLabels = b.getBookingSeats().stream()
                .map(bs -> bs.getShowSeat().getSeatRow() + bs.getShowSeat().getSeatNumber())
                .toList();

            PaymentStatus paymentStatus = b.getPayment() != null ? b.getPayment().getStatus() : null;

            return new UserBookingHistoryResponse(
                b.getId(),
                b.getShow().getEvent().getTitle(),
                b.getShow().getVenue().getName(),
                b.getShow().getStartTime(),
                seatLabels,
                b.getTotalAmount(),
                b.getStatus(),
                paymentStatus,
                b.getCreatedAt()
            );
        }).toList();
    }

    /**
     * Booking Cancellation:
     * 1. Verifies booking ownership against the authenticated user.
     * 2. Cutoff Rule: Ensures the show start time is at least 2 hours in the future.
     * 3. Locks booking and associated seats with PESSIMISTIC_WRITE (ordered by ID ASC).
     * 4. Transitions Booking to CANCELLED.
     * 5. If previously SUCCEEDED, transitions Payment to REFUNDED.
     * 6. Releases all ShowSeats back to AVAILABLE, resetting hold timestamps and user references.
     */
    @Transactional
    public BookingResponse cancelBooking(String userEmail, Long bookingId) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        Booking booking = bookingRepository.findByIdWithLock(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(user.getId()) && user.getRole() != com.ticketbooking.model.enums.Role.ADMIN) {
            throw new BadRequestException("Booking does not belong to the authenticated user");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled");
        }

        Instant cutoff = Instant.now().plus(2, ChronoUnit.HOURS);
        if (booking.getShow().getStartTime().isBefore(cutoff)) {
            throw new BadRequestException("Cannot cancel booking within 2 hours of show start time");
        }

        // Lock seats ascending
        List<Long> seatIds = booking.getBookingSeats().stream()
            .map(bs -> bs.getShowSeat().getId())
            .sorted()
            .toList();

        List<ShowSeat> seats = showSeatRepository.findByIdInOrderByIdAscWithLock(seatIds);

        // Mark booking cancelled
        booking.setStatus(BookingStatus.CANCELLED);

        // Mark payment refunded if it was succeeded
        paymentRepository.findByBookingId(booking.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            }
        });

        // Release seats back to AVAILABLE
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setHeldByUser(null);
        }
        showSeatRepository.saveAll(seats);
        Booking savedBooking = bookingRepository.save(booking);

        return mapToBookingResponse(savedBooking);
    }

    private BookingResponse mapToBookingResponse(Booking booking) {
        Instant holdExpiresAt = booking.getBookingSeats().stream()
            .map(bs -> bs.getShowSeat().getHoldExpiresAt())
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);

        return mapToBookingResponse(booking, holdExpiresAt);
    }

    private BookingResponse mapToBookingResponse(Booking booking, Instant holdExpiresAt) {
        List<BookingSeatResponse> seatResponses = booking.getBookingSeats().stream().map(bs ->
            new BookingSeatResponse(
                bs.getShowSeat().getId(),
                bs.getShowSeat().getSeatRow(),
                bs.getShowSeat().getSeatNumber(),
                bs.getPriceAtBooking()
            )
        ).toList();

        Show show = booking.getShow();
        return new BookingResponse(
            booking.getId(),
            booking.getUser().getId(),
            show.getId(),
            show.getEvent().getTitle(),
            show.getVenue().getName(),
            show.getStartTime(),
            booking.getIdempotencyKey(),
            booking.getTotalAmount(),
            booking.getStatus(),
            holdExpiresAt,
            booking.getCreatedAt(),
            seatResponses
        );
    }
}
