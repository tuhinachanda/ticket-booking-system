package com.ticketbooking;

import com.ticketbooking.dto.request.CreateBookingRequest;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.model.BookingSeat;
import com.ticketbooking.model.Show;
import com.ticketbooking.model.ShowSeat;
import com.ticketbooking.model.User;
import com.ticketbooking.model.enums.Role;
import com.ticketbooking.model.enums.SeatStatus;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.repository.ShowSeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private UserRepository userRepository;

    private Show testShow;
    private ShowSeat targetSeat;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        testShow = showRepository.findAll().stream().findFirst().orElseThrow();
        List<ShowSeat> seats = showSeatRepository.findByShowId(testShow.getId());

        targetSeat = seats.get(0);
        targetSeat.setStatus(SeatStatus.AVAILABLE);
        targetSeat.setHoldExpiresAt(null);
        targetSeat.setHeldByUser(null);
        targetSeat = showSeatRepository.save(targetSeat);

        // Ensure 10 distinct users exist for concurrent booking attempts
        testUsers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String email = "concurrent_user_" + i + "@test.com";
            User u = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                    .name("Concurrent User " + email)
                    .email(email)
                    .passwordHash("password")
                    .role(Role.USER)
                    .build())
            );
            testUsers.add(u);
        }
    }

    @Test
    @DisplayName("Concurrency: 10 parallel threads race for the exact same seat simultaneously -> exactly 1 succeeds, 9 fail with Conflict")
    void testConcurrentBookingForSameSeat() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        CreateBookingRequest request = new CreateBookingRequest(testShow.getId(), List.of(targetSeat.getId()));

        for (int i = 0; i < threadCount; i++) {
            final String userEmail = testUsers.get(i).getEmail();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // All 10 threads wait here and release simultaneously
                    startLatch.await();

                    bookingService.createBooking(userEmail, request, UUID.randomUUID().toString());
                    successCount.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    // Mapped to HTTP 409 Conflict
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                }
            });
        }

        // Wait until all threads are ready
        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        // Release all 10 threads at the exact same instant
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Assert concurrency outcomes
        assertThat(unexpectedErrorCount.get()).isEqualTo(0);
        assertThat(successCount.get())
            .as("Exactly 1 concurrent request must win the reservation")
            .isEqualTo(1);
        assertThat(conflictCount.get())
            .as("Exactly 9 concurrent requests must fail with 409 Conflict (SeatUnavailableException)")
            .isEqualTo(9);

        // Verify database integrity: only 1 booking record exists for targetSeat
        List<BookingSeat> bookingsForSeat = bookingSeatRepository.findByShowSeatId(targetSeat.getId());
        assertThat(bookingsForSeat).hasSize(1);

        ShowSeat seatInDb = showSeatRepository.findById(targetSeat.getId()).orElseThrow();
        assertThat(seatInDb.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seatInDb.getHeldByUser()).isNotNull();
    }
}
