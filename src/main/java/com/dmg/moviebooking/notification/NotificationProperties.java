package com.dmg.moviebooking.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        long reminderWindowHours,
        long reminderScanIntervalMs,
        int reminderScanBatchSize
) {
}
