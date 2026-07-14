package com.dmg.moviebooking.catalog.service;

import com.dmg.moviebooking.booking.service.ShowSeatGenerationService;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowRequest;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.dmg.moviebooking.catalog.entity.Movie;
import com.dmg.moviebooking.catalog.entity.Screen;
import com.dmg.moviebooking.catalog.entity.Show;
import com.dmg.moviebooking.catalog.repository.ShowRepository;
import com.dmg.moviebooking.catalog.repository.ShowSpecifications;
import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieService movieService;
    private final ScreenService screenService;
    private final ShowSeatGenerationService showSeatGenerationService;

    /** Mapped to DTOs inside this read-only transaction — movie/screen/theater are lazy associations that would throw LazyInitializationException if touched after the session closes (open-in-view=false). */
    public Page<ShowResponse> search(Long cityId, Long movieId, Instant from, Instant to, Pageable pageable) {
        return showRepository.findAll(ShowSpecifications.filter(cityId, movieId, from, to), pageable)
                .map(ShowResponse::from);
    }

    public Show findById(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Show", id));
    }

    /**
     * Creates a show and materializes its seat inventory in the same transaction, so a show is
     * never visible without bookable seats.
     */
    @Transactional
    public ShowResponse create(ShowRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleViolationException("Show startTime must be before endTime");
        }
        Movie movie = movieService.findById(request.movieId());
        Screen screen = screenService.findById(request.screenId());
        if (showRepository.existsOverlapping(screen.getId(), request.startTime(), request.endTime())) {
            throw new BusinessRuleViolationException("Screen already has an overlapping show in this time window");
        }
        Show show = Show.builder()
                .movie(movie)
                .screen(screen)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();
        show = showRepository.save(show);
        showSeatGenerationService.generateForShow(show);
        return ShowResponse.from(show);
    }
}
