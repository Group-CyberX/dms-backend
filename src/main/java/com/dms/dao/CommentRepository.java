package com.dms.dao;

import com.dms.models.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    
    // Retrieve all non-deleted comments for a given share token
    List<Comment> findByTokenAndIsDeletedFalse(String token);
}
