package com.dms.dao;

import com.dms.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository; // Use JpaRepository
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    //Fins notifications which are not yet deleted
    List<Notification> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID userId);
    //Find notifications which are unread
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false AND n.isDeleted = false")
    void markAllAsRead(@Param("userId") UUID userId);

    // Soft deleting notifications
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isDeleted = true WHERE n.notificationId = :id")
    void softDeleteById(@Param("id") UUID id);
}