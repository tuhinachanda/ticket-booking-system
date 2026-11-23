package com.ticketbooking.repository;

import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowId(Long showId);

    List<ShowSeat> findByShowIdAndStatus(Long showId, SeatStatus status);

    long countByShowIdAndStatus(Long showId, SeatStatus status);

    Optional<ShowSeat> findByShowIdAndSeatRowAndSeatNumber(Long showId, String seatRow, Integer seatNumber);

    long countByShowId(Long showId);

    /**
     * Calculates available seats dynamically:
     * A seat is counted as available if its status is AVAILABLE, OR if its status is HELD but the hold
     * expiration lease has elapsed (holdExpiresAt < :now).
     */
    @Query("""
        SELECT COUNT(s) FROM ShowSeat s
        WHERE s.show.id = :showId
          AND (
            s.status = com.ticketbooking.model.enums.SeatStatus.AVAILABLE
            OR (s.status = com.ticketbooking.model.enums.SeatStatus.HELD AND s.holdExpiresAt < :now)
          )
    """)
    long countAvailableSeats(@Param("showId") Long showId, @Param("now") Instant now);

    @Query("SELECT s FROM ShowSeat s WHERE s.show.id = :showId ORDER BY s.seatRow ASC, s.seatNumber ASC")
    List<ShowSeat> findByShowIdOrderBySeatRowAscSeatNumberAsc(@Param("showId") Long showId);

    /**
     * Deadlock Prevention & Concurrency Locking:
     * Acquires pessimistic write locks on requested seats ordered strictly by ID ascending.
     * Enforcing a consistent global lock acquisition order (seat ID ASC) guarantees that concurrent
     * transactions attempting to book or hold overlapping seats never enter a circular wait condition.
     * The lock timeout hint ensures requests fail fast rather than stalling threads indefinitely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT s FROM ShowSeat s WHERE s.id IN :seatIds ORDER BY s.id ASC")
    List<ShowSeat> findByIdInOrderByIdAscWithLock(@Param("seatIds") List<Long> seatIds);

    /**
     * Hold Expiration:
     * Queries for seats currently marked as HELD whose hold timestamp has expired, allowing a cleanup
     * worker or checkout query to release them back to AVAILABLE.
     */
    List<ShowSeat> findByStatusAndHoldExpiresAtBefore(SeatStatus status, Instant now);
}
