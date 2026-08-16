package com.dms.dao;

import com.dms.models.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    List<RetentionPolicy> findByActiveTrue();

    List<RetentionPolicy> findAllByOrderByCreatedAtDesc();
}
