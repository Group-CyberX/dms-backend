package com.dms.dao;

import com.dms.dto.SearchHistoryResponseDTO;
import com.dms.models.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, UUID> {

    @Query("SELECT new com.dms.dto.SearchHistoryResponseDTO(sl.searchId, sl.query, d.document_id, d.title, sl.timestamp) " +
           "FROM SearchLog sl JOIN Documents d ON sl.clickedDocId = d.document_id " +
           "ORDER BY sl.timestamp DESC")
    List<SearchHistoryResponseDTO> findSearchHistory();
}
