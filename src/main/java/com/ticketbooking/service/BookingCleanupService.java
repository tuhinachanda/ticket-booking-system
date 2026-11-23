package com.ticketbooking.service;

import com.ticketbooking.model.Booking;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.enums.BookingStatus;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled worker for releasing expired seat holds.
 *
 * Hold Expiration Logic:
 * Runs periodically (every 30 seconds) to find seats that were temporarily held for a user
 * whose 5-minute lease has elapsed without completed payment.
 * 1. Marks associated PENDING bookings as EXPIRED.
 * 2. Resets HELD seats back to AVAILABLE, clearing the hold expiration timestamp and user reference.
 */
@Service
public class BookingCleanupService {

    private static final Logger log = LoggerFactory.getLogger(BookingCleanupService.class);

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;

    public BookingCleanupService(BookingRepository bookingRepository, ShowSeatRepository showSeatRepository) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void cleanupExpiredHolds() {
        Instant now = Instant.now();

        // 1. Mark pending bookings with expired seat holds as EXPIRED
        List<Booking> expiredBookings = bookingRepository.findPendingBookingsWithExpiredSeats(now);
        if (!expiredBookings.isEmpty()) {
            for (Booking booking : expiredBookings) {
                booking.setStatus(BookingStatus.EXPIRED);
            }
            bookingRepository.saveAll(expiredBookings);
            log.info("Expired {} pending bookings due to elapsed hold lease.", expiredBookings.size());
        }

        // 2. Release expired HELD seats back to AVAILABLE
        List<ShowSeat> expiredSeats = showSeatRepository.findByStatusAndHoldExpiresAtBefore(SeatStatus.HELD, now);
        if (!expiredSeats.isEmpty()) {
            for (ShowSeat seat : expiredSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHoldExpiresAt(null);
                seat.setHeldByUser(null);
            }
            showSeatRepository.saveAll(expiredSeats);
            log.info("Released {} expired held seats back to AVAILABLE.", expiredSeats.size());
        }
    }
}
