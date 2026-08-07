package com.dms.dao;

import com.dms.models.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    // Find a tag by name ignoring letter case
    @Query("SELECT t FROM Tag t WHERE LOWER(t.tag_name) = LOWER(:tagName)")
    Optional<Tag> findByTagNameIgnoreCase(@Param("tagName") String tagName);
}
