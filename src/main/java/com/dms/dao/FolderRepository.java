package com.dms.dao;

import com.dms.models.Folders;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folders, UUID> {
    Optional<Folders> findByNameIgnoreCase(String name);
}
