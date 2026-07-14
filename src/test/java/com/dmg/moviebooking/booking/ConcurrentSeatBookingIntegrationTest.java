package com.dmg.moviebooking.booking;

import com.dmg.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.dmg.moviebooking.booking.dto.BookingDtos.ConfirmRequest;
import com.dmg.moviebooking.booking.dto.BookingDtos.HoldRequest;
import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.entity.BookingStatus;
import com.dmg.moviebooking.booking.entity.ShowSeat;
import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import com.dmg.moviebooking.booking.service.BookingService;
import com.dmg.moviebooking.booking.service.HoldExpiryReleaseService;
import com.dmg.moviebooking.common.exception.ConflictException;
import com.dmg.moviebooking.support.AbstractIntegrationTest;
import com.dmg.moviebooking.user.entity.Role;
import com.dmg.moviebooking.user.entity.User;
import com.dmg.moviebooking.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline correctness guarantee: concurrent requests for the same seat(s) must serialize
 * without double-allocation. Each thread calls BookingService directly (not through MockMvc) so it
 * gets its own real transaction/connection against Testcontainers Postgres — routing everything
 * through a single test thread or wrapping the test in @Transactional would prove nothing.
 */
class ConcurrentSeatBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private HoldExpiryReleaseService holdExpiryReleaseService;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createCustomer(String email) {
        User user = User.builder()
                .name("Racer")
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.CUSTOMER)
                .createdAt(Instant.now())
                .build();
        return userRepository.save(user).getId();
    }

    @Test
    void exactlyOneWinsWhenManyUsersRaceForTheSameSeat() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        Long targetSeatId = seatIdsFor(showId).get(0);

        int racers = 12;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            userIds.add(createCustomer("racer-" + i + "-" + System.nanoTime() + "@test.com"));
        }

        AtomicInteger successCount = new AtomicInteger();
        ConcurrentLinkedQueue<Exception> conflicts = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers);

        for (Long userId : userIds) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bookingService.hold(userId, new HoldRequest(showId, List.of(targetSeatId)));
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    conflicts.add(ex);
                }
                return null;
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflicts).hasSize(racers - 1);
        assertThat(conflicts).allMatch(ConflictException.class::isInstance);

        ShowSeat seat = showSeatRepository.findById(targetSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(seat.getHeldByBookingId()).isNotNull();
    }

    @Test
    void overlappingMultiSeatRequestsResolveCleanlyWithoutDeadlock() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        List<Long> seats = seatIdsFor(showId);
        Long seatA = seats.get(1);
        Long seatShared = seats.get(2);
        Long seatB = seats.get(3);

        Long userA = createCustomer("overlap-a-" + System.nanoTime() + "@test.com");
        Long userB = createCustomer("overlap-b-" + System.nanoTime() + "@test.com");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<Object> results = new ConcurrentLinkedQueue<>();

        pool.submit(() -> {
            ready.countDown();
            try {
                go.await();
                results.add(bookingService.hold(userA, new HoldRequest(showId, List.of(seatA, seatShared))));
            } catch (Exception ex) {
                results.add(ex);
            }
            return null;
        });
        pool.submit(() -> {
            ready.countDown();
            try {
                go.await();
                results.add(bookingService.hold(userB, new HoldRequest(showId, List.of(seatShared, seatB))));
            } catch (Exception ex) {
                results.add(ex);
            }
            return null;
        });

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long successes = results.stream().filter(BookingResponse.class::isInstance).count();
        long failures = results.stream().filter(ConflictException.class::isInstance).count();
        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        ShowSeat shared = showSeatRepository.findById(seatShared).orElseThrow();
        assertThat(shared.getStatus()).isEqualTo(ShowSeatStatus.HELD);

        // The losing request's *exclusive* seat must have rolled back to AVAILABLE, not been left
        // dangling HELD — proving the whole failed transaction rolled back cleanly.
        BookingResponse winner = (BookingResponse) results.stream().filter(BookingResponse.class::isInstance).findFirst().orElseThrow();
        Long loserExclusiveSeat = winner.seats().stream().anyMatch(s -> s.showSeatId().equals(seatA)) ? seatB : seatA;
        ShowSeat loserSeat = showSeatRepository.findById(loserExclusiveSeat).orElseThrow();
        assertThat(loserSeat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void sweepAndConfirmRaceResolveToExactlyOneOutcome() throws Exception {
        String adminToken = adminLogin();
        Long showId = createShowFixture(adminToken);
        Long seatId = seatIdsFor(showId).get(4);
        Long userId = createCustomer("race-expiry-" + System.nanoTime() + "@test.com");

        BookingResponse booking = bookingService.hold(userId, new HoldRequest(showId, List.of(seatId)));

        // Simulate the hold having just expired, without waiting on real wall-clock minutes.
        ShowSeat seat = showSeatRepository.findById(seatId).orElseThrow();
        seat.setHoldExpiresAt(Instant.now().minusSeconds(1));
        showSeatRepository.saveAndFlush(seat);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<Object> results = new ConcurrentLinkedQueue<>();

        pool.submit(() -> {
            ready.countDown();
            try {
                go.await();
                results.add(bookingService.confirm(userId, booking.id(), new ConfirmRequest(null)));
            } catch (Exception ex) {
                results.add(ex);
            }
            return null;
        });
        pool.submit(() -> {
            ready.countDown();
            try {
                go.await();
                holdExpiryReleaseService.releaseIfExpired(booking.id());
                results.add("swept");
            } catch (Exception ex) {
                results.add(ex);
            }
            return null;
        });

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        Booking finalBooking = bookingRepository.findById(booking.id()).orElseThrow();
        ShowSeat finalSeat = showSeatRepository.findById(seatId).orElseThrow();

        if (finalBooking.getStatus() == BookingStatus.CONFIRMED) {
            assertThat(finalSeat.getStatus()).isEqualTo(ShowSeatStatus.BOOKED);
        } else {
            assertThat(finalBooking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(finalSeat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        }
    }
}
