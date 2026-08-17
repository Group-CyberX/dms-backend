package com.dms.dao;

import com.dms.models.SettingEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettingEntryRepository extends JpaRepository<SettingEntry, UUID> {

    Optional<SettingEntry> findByScopeAndOwnerId(String scope, UUID ownerId);

    Optional<SettingEntry> findFirstByScope(String scope);
}
