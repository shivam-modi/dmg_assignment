package com.dmg.moviebooking.catalog.repository;

import com.dmg.moviebooking.catalog.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
