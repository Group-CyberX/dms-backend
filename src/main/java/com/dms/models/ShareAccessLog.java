package com.dms.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "share_access_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String token;

    private UUID documentId;

    private UUID userId;

    private LocalDateTime accessedAt;

    @Builder.Default
    private boolean downloaded = false;

    @Builder.Default
    private boolean commented = false;

}
