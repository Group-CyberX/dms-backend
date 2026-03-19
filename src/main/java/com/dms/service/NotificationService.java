package com.dms.service;

import com.dms.dao.NotificationRepository;
import com.dms.models.Notification;
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

    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true); // Updated setter
            notificationRepository.save(notification);
        });
    }
}