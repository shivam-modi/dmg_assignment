package com.dmg.moviebooking.catalog.service;

import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterRequest;
import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterResponse;
import com.dmg.moviebooking.catalog.entity.City;
import com.dmg.moviebooking.catalog.entity.Theater;
import com.dmg.moviebooking.catalog.repository.TheaterRepository;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TheaterService {

    private final TheaterRepository theaterRepository;
    private final CityService cityService;

    /**
     * Returns DTOs (not entities) mapped inside this read-only transaction: {@code city} is a lazy
     * association, and with {@code open-in-view=false} it would throw LazyInitializationException
     * if accessed later from the controller after the session closes.
     */
    public List<TheaterResponse> findByCity(Long cityId) {
        return theaterRepository.findByCityId(cityId).stream().map(TheaterResponse::from).toList();
    }

    public Theater findById(Long id) {
        return theaterRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Theater", id));
    }

    @Transactional
    public Theater create(TheaterRequest request) {
        City city = cityService.findById(request.cityId());
        Theater theater = Theater.builder()
                .city(city)
                .name(request.name())
                .address(request.address())
                .build();
        return theaterRepository.save(theater);
    }

    @Transactional
    public Theater update(Long id, TheaterRequest request) {
        Theater theater = findById(id);
        theater.setCity(cityService.findById(request.cityId()));
        theater.setName(request.name());
        theater.setAddress(request.address());
        return theater;
    }

    @Transactional
    public void delete(Long id) {
        if (!theaterRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Theater", id);
        }
        theaterRepository.deleteById(id);
    }
}
