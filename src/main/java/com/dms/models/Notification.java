package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "notification_id") // Maps to DB
    private UUID notificationId;      // Java variable

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @JsonProperty("isRead")
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Notification() {}

    public Notification(UUID userId, String message) {
        this.userId = userId;
        this.message = message;
        this.isRead = false;
    }

    // --- Updated Getters and Setters ---
    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isIsRead() {
        return isRead;
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}