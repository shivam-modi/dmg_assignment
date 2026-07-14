package com.dmg.moviebooking.catalog.service;

import com.dmg.moviebooking.catalog.dto.ScreenDtos.ScreenRequest;
import com.dmg.moviebooking.catalog.dto.SeatDtos.SeatLayoutRequest;
import com.dmg.moviebooking.catalog.entity.Screen;
import com.dmg.moviebooking.catalog.entity.Seat;
import com.dmg.moviebooking.catalog.entity.Theater;
import com.dmg.moviebooking.catalog.repository.ScreenRepository;
import com.dmg.moviebooking.catalog.repository.SeatRepository;
import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenService {

    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final TheaterService theaterService;

    public List<Screen> findByTheater(Long theaterId) {
        return screenRepository.findByTheaterId(theaterId);
    }

    public Screen findById(Long id) {
        return screenRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Screen", id));
    }

    public List<Seat> findSeats(Long screenId) {
        findById(screenId);
        return seatRepository.findByScreenId(screenId);
    }

    @Transactional
    public Screen create(ScreenRequest request) {
        Theater theater = theaterService.findById(request.theaterId());
        return screenRepository.save(Screen.builder().theater(theater).name(request.name()).build());
    }

    /** Bulk-creates the seat layout for a screen. Rejects rows that would duplicate an existing (row, seatNumber). */
    @Transactional
    public List<Seat> defineSeatLayout(Long screenId, SeatLayoutRequest request) {
        Screen screen = findById(screenId);
        List<Seat> seats = new ArrayList<>();
        for (SeatLayoutRequest.RowSpec row : request.rows()) {
            for (int n = 1; n <= row.seatCount(); n++) {
                if (seatRepository.existsByScreenIdAndRowLabelAndSeatNumber(screenId, row.rowLabel(), n)) {
                    throw new BusinessRuleViolationException(
                            "Seat %s%d already exists for screen %d".formatted(row.rowLabel(), n, screenId));
                }
                seats.add(Seat.builder()
                        .screen(screen)
                        .rowLabel(row.rowLabel())
                        .seatNumber(n)
                        .seatType(row.seatType())
                        .build());
            }
        }
        return seatRepository.saveAll(seats);
    }
}
