package com.dms.dao;

import com.dms.models.IntegrationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationMappingRepository extends JpaRepository<IntegrationMapping, UUID> {
    List<IntegrationMapping> findByErpConnectionId(UUID erpConnectionId);
    List<IntegrationMapping> findByErpConnectionIdAndEntityType(UUID erpConnectionId, String entityType);
    void deleteByErpConnectionId(UUID erpConnectionId);
}
