package com.dms.dao;

import com.dms.models.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.UUID;

public interface MetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    
}