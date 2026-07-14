package com.dmg.moviebooking.catalog.repository;

import com.dmg.moviebooking.catalog.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenId(Long screenId);

    boolean existsByScreenIdAndRowLabelAndSeatNumber(Long screenId, String rowLabel, Integer seatNumber);
}
