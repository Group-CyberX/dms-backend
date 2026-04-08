package com.dms.service;

import com.dms.dao.NotificationRepository;
import com.dms.models.Notification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void sendNotification(UUID userId, String message) {
        Notification notification = new Notification(userId, message);
        notification.setCreatedAt(java.time.LocalDateTime.now()); // Explicitly set it here
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }

    public List<Notification> getUserNotifications(UUID userId) {
        // Updated method call
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(UUID userId) {
        // Updated method call
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setIsRead(true); // Use the new explicit setter
            notificationRepository.saveAndFlush(notification); // Use saveAndFlush to force it
        });
    }

    @Transactional
    public void markAllUserNotificationsAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }
}