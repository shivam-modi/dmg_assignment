package com.dmg.moviebooking.notification.repository;

import com.dmg.moviebooking.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
