package com.dmg.moviebooking.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        long holdDurationMinutes,
        long holdSweepIntervalMs,
        int holdSweepBatchSize,
        long lockTimeoutMs
) {
}
