package com.ticketbooking.repository;

import com.ticketbooking.model.Show;
import com.ticketbooking.model.enums.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByEventId(Long eventId);

    List<Show> findByVenueId(Long venueId);

    List<Show> findByStatusAndStartTimeAfterOrderByStartTimeAsc(ShowStatus status, Instant after);

    @Query("""
        SELECT s FROM Show s
        JOIN FETCH s.venue v
        JOIN FETCH s.event e
        WHERE s.event.id = :eventId AND s.status = :status
        ORDER BY s.startTime ASC
    """)
    List<Show> findByEventIdAndStatusWithVenue(@Param("eventId") Long eventId, @Param("status") ShowStatus status);

    @Query("""
        SELECT s FROM Show s
        JOIN FETCH s.venue v
        JOIN FETCH s.event e
        WHERE s.id = :id
    """)
    Optional<Show> findByIdWithVenueAndEvent(@Param("id") Long id);
}
