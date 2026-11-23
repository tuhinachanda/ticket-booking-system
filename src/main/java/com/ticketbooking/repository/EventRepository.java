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
            WHERE (:city IS NULL OR LOWER(v.city) = LOWER(:city))
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (
                :search IS NULL OR
                LOWER(e.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
        """,
        countQuery = """
            SELECT COUNT(DISTINCT e) FROM Event e
            LEFT JOIN Show s ON s.event = e
            LEFT JOIN s.venue v
            WHERE (:city IS NULL OR LOWER(v.city) = LOWER(:city))
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (
                :search IS NULL OR
                LOWER(e.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%'))
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
