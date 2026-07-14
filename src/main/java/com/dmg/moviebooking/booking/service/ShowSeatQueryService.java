package com.dmg.moviebooking.booking.service;

import com.dmg.moviebooking.booking.dto.ShowSeatDtos.ShowSeatResponse;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowSeatQueryService {

    private final ShowSeatRepository showSeatRepository;

    public List<ShowSeatResponse> getSeatMap(Long showId) {
        return showSeatRepository.findByShowIdOrderBySeatId(showId).stream()
                .map(ShowSeatResponse::from)
                .toList();
    }
}
