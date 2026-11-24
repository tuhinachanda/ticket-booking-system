package com.ticketbooking;

import com.ticketbooking.dto.response.EventResponse;
import com.ticketbooking.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventSearchIntegrationTest {

    @Autowired
    private EventService eventService;

    @Test
    @DisplayName("Search Events with all null parameters succeeds without lower(bytea) error")
    void testSearchEventsWithAllNullParameters() {
        Page<EventResponse> result = eventService.searchEvents(null, null, null, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("Search Events with all empty/blank parameters succeeds")
    void testSearchEventsWithBlankParameters() {
        Page<EventResponse> result = eventService.searchEvents("   ", "", "  ", PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("Search Events with city filter only")
    void testSearchEventsWithCityFilter() {
        Page<EventResponse> result = eventService.searchEvents("Chicago", null, null, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("Search Events with category filter only")
    void testSearchEventsWithCategoryFilter() {
        Page<EventResponse> result = eventService.searchEvents(null, "Music", null, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent()).allMatch(e -> "Music".equalsIgnoreCase(e.category()));
    }

    @Test
    @DisplayName("Search Events with search keyword filter matching title or description")
    void testSearchEventsWithSearchKeyword() {
        Page<EventResponse> result = eventService.searchEvents(null, null, "acoustic", PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).description().toLowerCase()).contains("acoustic");
    }

    @Test
    @DisplayName("Search Events with combined city, category, and keyword")
    void testSearchEventsWithCombinedFilters() {
        Page<EventResponse> result = eventService.searchEvents("Chicago", "Music", "Music", PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("Search Events with non-matching query returns empty page")
    void testSearchEventsWithNoMatch() {
        Page<EventResponse> result = eventService.searchEvents("NonExistentCity999", null, null, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }
}
