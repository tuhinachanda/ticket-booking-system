package com.ticketbooking.service;

import com.ticketbooking.dto.request.SimulatePaymentRequest;
import com.ticketbooking.dto.response.PaymentResponse;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.Payment;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.PaymentStatus;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.PaymentRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public PaymentService(
        BookingRepository bookingRepository,
        ShowSeatRepository showSeatRepository,
        PaymentRepository paymentRepository,
        UserRepository userRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Simulates Payment Processing under Transactional Concurrency Control:
     *
     * 1. Concurrency Locking:
     *    Locks the target Booking row and all its associated ShowSeats using PESSIMISTIC_WRITE.
     *    Seat IDs are strictly sorted in ascending order to prevent circular deadlocks with other transactions.
     *
     * 2. Hold Expiration Guard:
     *    Checks if the temporary 5-minute hold lease has expired before finalizing payment.
     *    If expired, the booking is transitioned to EXPIRED, seats are released to AVAILABLE, and a
     *    SeatUnavailableException is thrown (mapped to HTTP 409 Conflict).
     *
     * 3. Successful Payment:
     *    Transitions Booking to CONFIRMED, marks seats permanently as BOOKED, clears hold expiration,
     *    and records/updates Payment status as SUCCEEDED with a generated transaction reference.
     *
     * 4. Failed Payment:
     *    Transitions Booking to CANCELLED, records Payment status as FAILED, and releases all reserved
     *    seats back to AVAILABLE so other users can book them immediately.
     */
    @Transactional
    public PaymentResponse simulatePayment(String userEmail, SimulatePaymentRequest request) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        // 1. Lock Booking row with pessimistic write lock
        Booking booking = bookingRepository.findByIdWithLock(request.bookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.bookingId()));

        // Ownership validation
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Booking does not belong to the authenticated user");
        }

        // Status validation
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Booking is not in PENDING status. Current status: " + booking.getStatus());
        }

        // Deadlock prevention: sort seat IDs in ascending order before locking
        List<Long> seatIds = booking.getBookingSeats().stream()
            .map(bs -> bs.getShowSeat().getId())
            .sorted()
            .toList();

        List<ShowSeat> seats = showSeatRepository.findByIdInOrderByIdAscWithLock(seatIds);

        Instant now = Instant.now();

        // Check hold expiration lease
        boolean hasExpired = seats.stream().anyMatch(
            s -> s.getHoldExpiresAt() != null && s.getHoldExpiresAt().isBefore(now)
        );

        if (hasExpired) {
            booking.setStatus(BookingStatus.EXPIRED);
            for (ShowSeat seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null);
                seat.setHeldByUser(null);
            }
            showSeatRepository.saveAll(seats);
            bookingRepository.save(booking);

            throw new SeatUnavailableException("Hold lease has expired for this booking. Seats have been released.");
        }

        Payment payment = paymentRepository.findByBookingId(booking.getId())
            .orElseGet(() -> Payment.builder()
                .booking(booking)
                .amount(booking.getTotalAmount())
                .build());

        if (Boolean.TRUE.equals(request.simulateSuccess())) {
            // Success scenario
            String transactionRef = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setTransactionRef(transactionRef);
            Payment savedPayment = paymentRepository.save(payment);

            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setPayment(savedPayment);

            for (ShowSeat seat : seats) {
                seat.setStatus(SeatStatus.BOOKED);
                seat.setHoldExpiresAt(null);
            }
            showSeatRepository.saveAll(seats);
            bookingRepository.save(booking);

            return new PaymentResponse(
                savedPayment.getId(),
                booking.getId(),
                savedPayment.getAmount(),
                savedPayment.getStatus(),
                booking.getStatus(),
                savedPayment.getTransactionRef(),
                savedPayment.getCreatedAt()
            );
        } else {
            // Failure scenario
            String transactionRef = "FAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setTransactionRef(transactionRef);
            Payment savedPayment = paymentRepository.save(payment);

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setPayment(savedPayment);

            // Release seats back to AVAILABLE
            for (ShowSeat seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null);
                seat.setHeldByUser(null);
            }
            showSeatRepository.saveAll(seats);
            bookingRepository.save(booking);

            return new PaymentResponse(
                savedPayment.getId(),
                booking.getId(),
                savedPayment.getAmount(),
                savedPayment.getStatus(),
                booking.getStatus(),
                savedPayment.getTransactionRef(),
                savedPayment.getCreatedAt()
            );
        }
    }
}
