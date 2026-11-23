package com.ticketbooking.model;

import com.ticketbooking.model.enums.SeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ShowSeat represents an individual bookable seat for a specific show.
 *
 * Concurrency & Locking Strategy:
 * 1. Optimistic Locking: The `@Version` field detects concurrent modifications. If two transactions
 *    attempt to hold or book the same seat at the same time, one will fail with OptimisticLockException.
 * 2. Hold Expiration: `holdExpiresAt` and `heldByUser` mark temporary seat holds. A background worker
 *    or query checks whether `holdExpiresAt < now()` to release expired holds back to `AVAILABLE`.
 * 3. Deadlock Prevention: When holding or booking multiple seats in a single transaction, the service
 *    orders seat IDs ascending before acquiring locks.
 */
@Entity
@Table(
    name = "show_seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_show_seats_seat", columnNames = {"show_id", "seat_row", "seat_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "seat_row", nullable = false, length = 10)
    private String seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SeatStatus status;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by_user_id")
    private User heldByUser;

    @Version
    @Column(nullable = false)
    private Long version;
}
