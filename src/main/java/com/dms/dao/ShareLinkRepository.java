package com.dms.dao;

import com.dms.models.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    // Find share link using unique token (used for access + validation)
    Optional<ShareLink> findByToken(String token);
}
