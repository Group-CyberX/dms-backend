package com.dms.dao;

import com.dms.models.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    // Find processing jobs for a list of document version IDs
    List<ProcessingJob> findByDocumentVersionIdIn(List<UUID> documentVersionIds);
}
