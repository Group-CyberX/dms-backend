package com.dms.dao;

import com.dms.models.UserSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSignatureRepository extends JpaRepository<UserSignature, UUID> {

    // Finds active configurations ignoring soft-deleted entries
    List<UserSignature> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<UserSignature> findByUserSignatureIdAndDeletedAtIsNull(UUID signatureId);

    @Modifying
    @Query("UPDATE UserSignature u SET u.isDefault = false WHERE u.userId = :userId")
    void clearDefaultStatusForUser(UUID userId);
}