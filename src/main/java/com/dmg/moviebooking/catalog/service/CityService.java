package com.dmg.moviebooking.catalog.service;

import com.dmg.moviebooking.catalog.entity.City;
import com.dmg.moviebooking.catalog.repository.CityRepository;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

    private final CityRepository cityRepository;

    public List<City> findAll() {
        return cityRepository.findAll();
    }

    public City findById(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("City", id));
    }

    @Transactional
    public City create(String name) {
        return cityRepository.save(City.builder().name(name).build());
    }

    @Transactional
    public City update(Long id, String name) {
        City city = findById(id);
        city.setName(name);
        return city;
    }

    @Transactional
    public void delete(Long id) {
        if (!cityRepository.existsById(id)) {
            throw ResourceNotFoundException.of("City", id);
        }
        cityRepository.deleteById(id);
    }
}
