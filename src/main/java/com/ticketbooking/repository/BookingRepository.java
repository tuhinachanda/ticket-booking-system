package com.ticketbooking.repository;

import com.ticketbooking.model.Booking;
import com.ticketbooking.model.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(BookingStatus status);

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b
        WHERE b.status = com.ticketbooking.model.enums.BookingStatus.CONFIRMED
    """)
    BigDecimal sumConfirmedRevenue();

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b
        WHERE b.show.id = :showId
          AND b.status = com.ticketbooking.model.enums.BookingStatus.CONFIRMED
    """)
    BigDecimal sumConfirmedRevenueByShowId(@Param("showId") Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT b FROM Booking b
        JOIN FETCH b.show s
        JOIN FETCH s.event e
        JOIN FETCH s.venue v
        LEFT JOIN FETCH b.payment p
        WHERE b.user.id = :userId
        ORDER BY b.createdAt DESC
    """)
    List<Booking> findHistoryByUserId(@Param("userId") Long userId);

    List<Booking> findByShowIdAndStatus(Long showId, BookingStatus status);

    /**
     * Hold Expiration Cleanup Query:
     * Finds all PENDING bookings that have at least one associated seat whose hold lease has expired.
     */
    @Query("""
        SELECT DISTINCT b FROM Booking b
        JOIN b.bookingSeats bs
        JOIN bs.showSeat s
        WHERE b.status = com.ticketbooking.model.enums.BookingStatus.PENDING
          AND s.status = com.ticketbooking.model.enums.SeatStatus.HELD
          AND s.holdExpiresAt < :now
    """)
    List<Booking> findPendingBookingsWithExpiredSeats(@Param("now") Instant now);
}
