package com.dmg.moviebooking.booking.service;

import com.dmg.moviebooking.booking.entity.ShowSeat;
import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import com.dmg.moviebooking.catalog.entity.Seat;
import com.dmg.moviebooking.catalog.entity.Show;
import com.dmg.moviebooking.catalog.repository.SeatRepository;
import com.dmg.moviebooking.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowSeatGenerationService {

    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PricingService pricingService;

    /**
     * Materializes one {@link ShowSeat} per physical seat of the show's screen, priced at creation
     * time. Called synchronously within the same transaction as show creation (see catalog's
     * ShowService) so a show is never persisted without its bookable seats.
     */
    @Transactional
    public void generateForShow(Show show) {
        List<Seat> seats = seatRepository.findByScreenId(show.getScreen().getId());
        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(ShowSeatStatus.AVAILABLE)
                        .price(pricingService.resolvePrice(seat.getSeatType(), show.getStartTime()))
                        .build())
                .toList();
        showSeatRepository.saveAll(showSeats);
    }
}
