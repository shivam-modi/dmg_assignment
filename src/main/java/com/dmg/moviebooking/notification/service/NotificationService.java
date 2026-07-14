package com.dmg.moviebooking.notification.service;

import com.dmg.moviebooking.notification.entity.Notification;
import com.dmg.moviebooking.notification.entity.NotificationStatus;
import com.dmg.moviebooking.notification.entity.NotificationType;
import com.dmg.moviebooking.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** Fully simulated delivery (no real email/SMS provider — out of scope): "sending" means logging and persisting a record. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    @Transactional
    public void send(Long userId, NotificationType type, Map<String, Object> payload) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel("EMAIL")
                .payload(payload)
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();
        notificationRepository.save(notification);
        log.info("Notification [{}] simulated-sent to user {}: {}", type, userId, payload);
    }
}
