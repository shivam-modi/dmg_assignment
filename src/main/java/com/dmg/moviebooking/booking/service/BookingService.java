package com.dmg.moviebooking.booking.service;

import com.dmg.moviebooking.booking.BookingProperties;
import com.dmg.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.dmg.moviebooking.booking.dto.BookingDtos.ConfirmRequest;
import com.dmg.moviebooking.booking.dto.BookingDtos.HoldRequest;
import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.entity.BookingSeat;
import com.dmg.moviebooking.booking.entity.BookingStatus;
import com.dmg.moviebooking.booking.entity.ShowSeat;
import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.booking.event.BookingCancelledEvent;
import com.dmg.moviebooking.booking.event.BookingConfirmedEvent;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.booking.repository.BookingSeatRepository;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import com.dmg.moviebooking.catalog.entity.Show;
import com.dmg.moviebooking.catalog.repository.ShowRepository;
import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.common.exception.ConflictException;
import com.dmg.moviebooking.common.exception.PaymentDeclinedException;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.common.exception.SeatLockTimeoutException;
import com.dmg.moviebooking.payment.entity.Payment;
import com.dmg.moviebooking.payment.entity.PaymentStatus;
import com.dmg.moviebooking.payment.service.PaymentService;
import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.service.DiscountService;
import com.dmg.moviebooking.refund.entity.Refund;
import com.dmg.moviebooking.refund.service.RefundService;
import com.dmg.moviebooking.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowRepository showRepository;
    private final BookingProperties bookingProperties;
    private final DiscountService discountService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Locks the requested seats (ordered SELECT ... FOR UPDATE) in one transaction, verifies every
     * one is AVAILABLE, and flips them to HELD alongside a new PENDING_PAYMENT booking. A second
     * concurrent request for an overlapping seat blocks on the row lock until this transaction
     * commits, then sees HELD (not AVAILABLE) and gets a clean 409 — no double allocation.
     */
    @Transactional
    public BookingResponse hold(Long userId, HoldRequest request) {
        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> ResourceNotFoundException.of("Show", request.showId()));
        if (show.getStartTime().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("Cannot book a show that has already started");
        }

        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        List<ShowSeat> seats = lockSeatsForUpdate(seatIds);

        if (seats.size() != seatIds.size()) {
            throw ResourceNotFoundException.of("ShowSeat", seatIds);
        }
        for (ShowSeat seat : seats) {
            if (!seat.getShow().getId().equals(request.showId())) {
                throw new BusinessRuleViolationException("Seat " + seat.getId() + " does not belong to show " + request.showId());
            }
            if (seat.getStatus() != ShowSeatStatus.AVAILABLE) {
                throw new ConflictException("Seat " + seat.getId() + " is no longer available");
            }
        }

        BigDecimal totalAmount = seats.stream().map(ShowSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(bookingProperties.holdDurationMinutes() * 60);

        Booking booking = bookingRepository.save(Booking.builder()
                .user(entityManager.getReference(User.class, userId))
                .show(show)
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(totalAmount)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());

        List<BookingSeat> bookingSeats = seats.stream().map(seat -> {
            seat.setStatus(ShowSeatStatus.HELD);
            seat.setHeldByBookingId(booking.getId());
            seat.setHoldExpiresAt(expiresAt);
            return BookingSeat.builder().booking(booking).showSeat(seat).priceAtBooking(seat.getPrice()).build();
        }).toList();
        bookingSeatRepository.saveAll(bookingSeats);

        return BookingResponse.from(booking, bookingSeats);
    }

    /**
     * Re-validates freshly under the row lock — status/expiry read here, at write time, not a value
     * read earlier in the request — which is what closes the race against the hold-expiry sweep.
     * Discount redemption and the (simulated) payment charge happen inside this same transaction; on
     * any failure the whole transaction rolls back, so the hold survives untouched and the caller
     * can retry within its remaining TTL rather than losing the seats outright.
     */
    @Transactional
    public BookingResponse confirm(Long userId, Long bookingId, ConfirmRequest request) {
        Booking booking = getOwnedBookingOrThrow(userId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not awaiting payment");
        }

        List<Long> expectedSeatIds = showSeatRepository.findHeldSeatIdsByBookingId(bookingId, ShowSeatStatus.HELD);
        List<ShowSeat> seats = lockSeatsByBookingForUpdate(bookingId);
        Instant now = Instant.now();
        boolean stillValid = seats.size() == expectedSeatIds.size()
                && !seats.isEmpty()
                && seats.stream().allMatch(s -> s.getStatus() == ShowSeatStatus.HELD
                        && bookingId.equals(s.getHeldByBookingId())
                        && s.getHoldExpiresAt() != null
                        && s.getHoldExpiresAt().isAfter(now));
        if (!stillValid) {
            throw new ConflictException("Seat hold has expired — please select seats again");
        }

        BigDecimal amountDue = booking.getTotalAmount();
        DiscountCode discountCode = null;
        if (request.discountCode() != null && !request.discountCode().isBlank()) {
            discountCode = discountService.validate(request.discountCode());
            amountDue = discountService.apply(discountCode, amountDue);
            discountService.redeemOrThrow(discountCode.getId());
        }

        Payment payment = paymentService.charge(bookingId, amountDue);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentDeclinedException("Payment was declined; your seats are still held until expiry");
        }

        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(amountDue);
        if (discountCode != null) {
            booking.setDiscountCodeId(discountCode.getId());
        }

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        BookingResponse response = BookingResponse.from(booking, bookingSeats);
        eventPublisher.publishEvent(new BookingConfirmedEvent(bookingId));
        return response;
    }

    /** Idempotency guard (only CONFIRMED can be cancelled) prevents double-refunding a retried cancel request. */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = getOwnedBookingOrThrow(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only confirmed bookings can be cancelled");
        }

        List<ShowSeat> seats = lockSeatsByBookingForUpdate(bookingId);
        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeatStatus.AVAILABLE);
            seat.setHeldByBookingId(null);
            seat.setHoldExpiresAt(null);
        }

        BigDecimal refundAmount = refundService.calculateRefundAmount(booking.getTotalAmount(), booking.getShow().getStartTime());
        Refund refund = refundService.processRefund(bookingId, refundAmount);
        booking.setStatus(BookingStatus.CANCELLED);

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        BookingResponse response = BookingResponse.from(booking, bookingSeats);
        eventPublisher.publishEvent(new BookingCancelledEvent(bookingId));
        return response;
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long userId, Long bookingId) {
        Booking booking = getOwnedBookingOrThrow(userId, bookingId);
        return BookingResponse.from(booking, bookingSeatRepository.findByBookingId(bookingId));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> listMine(Long userId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(b -> BookingResponse.from(b, bookingSeatRepository.findByBookingId(b.getId())));
    }

    private Booking getOwnedBookingOrThrow(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            // Don't leak existence of another user's booking.
            throw ResourceNotFoundException.of("Booking", bookingId);
        }
        return booking;
    }

    private List<ShowSeat> lockSeatsForUpdate(List<Long> seatIds) {
        applyLockTimeout();
        try {
            return showSeatRepository.lockByIdsForUpdate(seatIds);
        } catch (PessimisticLockingFailureException | QueryTimeoutException ex) {
            throw new SeatLockTimeoutException("Timed out waiting to lock seats — please try again");
        }
    }

    private List<ShowSeat> lockSeatsByBookingForUpdate(Long bookingId) {
        applyLockTimeout();
        try {
            return showSeatRepository.lockByBookingIdForUpdate(bookingId);
        } catch (PessimisticLockingFailureException | QueryTimeoutException ex) {
            throw new SeatLockTimeoutException("Timed out waiting to lock seats — please try again");
        }
    }

    private void applyLockTimeout() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '" + bookingProperties.lockTimeoutMs() + "ms'").executeUpdate();
    }
}
