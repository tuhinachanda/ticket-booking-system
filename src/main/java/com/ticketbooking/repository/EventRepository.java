package com.ticketbooking.repository;

import com.ticketbooking.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCategoryIgnoreCase(String category);

    List<Event> findByTitleContainingIgnoreCase(String keyword);

    @Query(
        value = """
            SELECT DISTINCT e FROM Event e
            LEFT JOIN Show s ON s.event = e
            LEFT JOIN s.venue v
            WHERE (COALESCE(CAST(:city AS string), '') = '' OR LOWER(v.city) = LOWER(CAST(:city AS string)))
              AND (COALESCE(CAST(:category AS string), '') = '' OR LOWER(e.category) = LOWER(CAST(:category AS string)))
              AND (
                COALESCE(CAST(:search AS string), '') = '' OR
                LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
              )
        """,
        countQuery = """
            SELECT COUNT(DISTINCT e) FROM Event e
            LEFT JOIN Show s ON s.event = e
            LEFT JOIN s.venue v
            WHERE (COALESCE(CAST(:city AS string), '') = '' OR LOWER(v.city) = LOWER(CAST(:city AS string)))
              AND (COALESCE(CAST(:category AS string), '') = '' OR LOWER(e.category) = LOWER(CAST(:category AS string)))
              AND (
                COALESCE(CAST(:search AS string), '') = '' OR
                LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
              )
        """
    )
    Page<Event> searchEvents(
        @Param("city") String city,
        @Param("category") String category,
        @Param("search") String search,
        Pageable pageable
    );
}
