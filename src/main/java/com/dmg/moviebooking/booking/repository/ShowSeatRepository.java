package com.dmg.moviebooking.booking.repository;

import com.dmg.moviebooking.booking.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderBySeatId(Long showId);

    boolean existsByShowId(Long showId);
}
