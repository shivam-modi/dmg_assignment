package com.dmg.moviebooking.catalog.repository;

import com.dmg.moviebooking.catalog.entity.Show;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class ShowSpecifications {

    private ShowSpecifications() {
    }

    public static Specification<Show> cityIdEquals(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return (root, query, cb) -> {
            var screen = root.join("screen");
            var theater = screen.join("theater");
            return cb.equal(theater.get("city").get("id"), cityId);
        };
    }

    public static Specification<Show> movieIdEquals(Long movieId) {
        if (movieId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("movie").get("id"), movieId);
    }

    public static Specification<Show> startTimeAfterOrEqual(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    public static Specification<Show> startTimeBeforeOrEqual(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startTime"), to);
    }

    public static Specification<Show> filter(Long cityId, Long movieId, Instant from, Instant to) {
        List<Specification<Show>> specs = Arrays.asList(
                        cityIdEquals(cityId),
                        movieIdEquals(movieId),
                        startTimeAfterOrEqual(from),
                        startTimeBeforeOrEqual(to))
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        return specs.isEmpty() ? null : Specification.allOf(specs);
    }
}
