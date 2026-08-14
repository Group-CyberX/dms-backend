package com.dms.service;

import com.dms.dao.NotificationRepository;
import com.dms.models.Notification;
import com.dms.security.SecurityUtils;
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

    private String getNotificationTitle(String message) {
        if (message == null) return "System Notification";
        String msg = message.toLowerCase();

        if (msg.contains("approved")) return "Document Approved";
        if (msg.contains("rejected")) return "Document Rejected";
        if (msg.contains("uploaded")) return "New Upload";
        if (msg.contains("workflow")) return "Workflow Update";
        if (msg.contains("commented")) return "New Comment";
        if (msg.contains("assigned")) return "Task Assigned";
        if (msg.contains("deadline")) return "Deadline Reminder";
        if (msg.contains("deleted")) return "Document Deleted";
        if (msg.contains("shared")) return "Document Shared";
        if (msg.contains("version")) return "Version Update";
        if (msg.contains("error") || msg.contains("failed")) return "Action Failed";
        if (msg.contains("requires your approval")) return "Action Required";
        if (msg.contains("modified") || msg.contains("edited")) return "Document Updated";
        if (msg.contains("new login") || msg.contains("logged in")) return "Security Alert";

        return "System Notification";
    }

    public void sendNotification(UUID userId, String message) {
        String title = getNotificationTitle(message);
        Notification notification = new Notification(userId, message, title);

        notification.setCreatedAt(java.time.LocalDateTime.now());
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    public void sendInternalSystemNotification(String message) {
        sendNotification(SecurityUtils.currentUserId(), message);
    }

    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(UUID userId) {
        // FIX: Now correctly queries only unread entries
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.saveAndFlush(notification); // Forces database synchronization
        });
    }

    @Transactional
    public void markAllUserNotificationsAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID notificationId) {
        notificationRepository.softDeleteById(notificationId);
    }
}