package com.dmg.moviebooking.booking.entity;

import com.dmg.moviebooking.catalog.entity.Seat;
import com.dmg.moviebooking.catalog.entity.Show;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per seat per show — the concurrency-critical table. Correctness of the whole booking
 * flow rests on how rows here are locked (ordered {@code SELECT ... FOR UPDATE}) and conditionally
 * updated (fresh status/expiry re-check under the lock), not on object-level relationships, which
 * is why {@code heldByBookingId} is a plain column rather than a {@code @ManyToOne} — every write
 * path goes through explicit repository queries, not entity graph navigation.
 */
@Entity
@Table(name = "show_seats")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowSeatStatus status;

    @Column(name = "held_by_booking_id")
    private Long heldByBookingId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(nullable = false)
    private BigDecimal price;
}
