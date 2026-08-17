package com.dms.dao;

import com.dms.models.ErpConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ErpConnectionRepository extends JpaRepository<ErpConnection, UUID> {
    List<ErpConnection> findByIsActiveTrue();
}
