package com.dmg.moviebooking.catalog.repository;

import com.dmg.moviebooking.catalog.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
}
