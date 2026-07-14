package com.dmg.moviebooking.catalog.service;

import com.dmg.moviebooking.catalog.dto.MovieDtos.MovieRequest;
import com.dmg.moviebooking.catalog.entity.Movie;
import com.dmg.moviebooking.catalog.repository.MovieRepository;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    public Movie findById(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Movie", id));
    }

    @Transactional
    public Movie create(MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .durationMinutes(request.durationMinutes())
                .language(request.language())
                .genre(request.genre())
                .build();
        return movieRepository.save(movie);
    }

    @Transactional
    public Movie update(Long id, MovieRequest request) {
        Movie movie = findById(id);
        movie.setTitle(request.title());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setLanguage(request.language());
        movie.setGenre(request.genre());
        return movie;
    }

    @Transactional
    public void delete(Long id) {
        if (!movieRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Movie", id);
        }
        movieRepository.deleteById(id);
    }
}
