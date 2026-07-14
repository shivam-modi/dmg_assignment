package com.dmg.moviebooking.catalog.repository;

import com.dmg.moviebooking.catalog.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ShowRepository extends JpaRepository<Show, Long>, JpaSpecificationExecutor<Show> {

    @Query("""
            SELECT COUNT(s) > 0 FROM Show s
            WHERE s.screen.id = :screenId
              AND s.startTime < :endTime
              AND s.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("screenId") Long screenId,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime);
}
