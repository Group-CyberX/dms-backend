package com.dms.models;

import com.dms.enums.AccessLevel;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "share_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "share_id", updatable = false, nullable = false)
    private UUID shareId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    // Expiry date of link (null = no expiry)
    @Column(name = "expiry_date", nullable = true)
    private LocalDateTime expiryDate;

    // Access level stored as ENUM string
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private AccessLevel accessLevel; 

    // Hashed password (if password protection enabled)
    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload;

    @Column(name = "allow_comments", nullable = false)
    private boolean allowComments;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}