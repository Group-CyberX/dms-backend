package com.dms.dao;

import com.dms.models.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, UUID> {

    List<ClassificationRule> findByActiveTrue();

    List<ClassificationRule> findAllByOrderByCreatedAtDesc();
}
