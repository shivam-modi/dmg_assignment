package com.dmg.moviebooking.pricing.repository;

import com.dmg.moviebooking.pricing.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCode(String code);

    /**
     * Atomically increments usage only if still valid at write time. A plain conditional UPDATE is
     * enough here (no explicit row lock needed): Postgres takes the row lock as part of the UPDATE
     * itself, and a second concurrent redemption attempt waits for it, then re-evaluates this WHERE
     * clause against the now-updated row — so two racing confirms can never both redeem the last
     * remaining use of a maxed-out code.
     */
    @Modifying
    @Query("""
            UPDATE DiscountCode d SET d.usedCount = d.usedCount + 1
            WHERE d.id = :id AND d.active = true AND d.usedCount < d.maxUses
              AND :now BETWEEN d.validFrom AND d.validTo
            """)
    int redeem(@Param("id") Long id, @Param("now") Instant now);
}
