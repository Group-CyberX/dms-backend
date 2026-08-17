package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
// Comments are only ever fetched by share token.
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_token", columnList = "token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String token;

    private UUID documentId;

    private UUID userId;

    private String content;

    @Builder.Default
    private boolean isDeleted = false;

     @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private UUID lastEditedBy;

    // ---- Anchor on the page --------------------------------------------
    // A comment can either be general (all three null, shown only in the
    // drawer) or anchored to a spot on the document, in which case it also
    // appears as a pin at that point.
    //
    // x and y are fractions of the page (0.0 - 1.0) measured from the
    // top-left, the same convention the signature placements use, so a pin
    // lands in the same place regardless of zoom or render width.

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "anchor_x")
    private Double anchorX;

    @Column(name = "anchor_y")
    private Double anchorY;

    /** Set for replies, giving the drawer its discussion threads. */
    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    /**
     * How this annotation is written into the document when a version is saved.
     *
     *   COMMENT - a numbered pin plus a note, listed on the summary page
     *   TEXT    - typewriter: the words themselves are drawn onto the page at
     *             the anchor, the way you would type onto a paper form
     *
     * Rows created before this column existed hold NULL, which reads as
     * COMMENT - see annotationTypeOrDefault().
     */
    @Column(name = "annotation_type")
    private String annotationType;

    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_TEXT = "TEXT";

    /** Null-safe accessor: an unset type is an ordinary comment. */
    public String annotationTypeOrDefault() {
        return annotationType == null || annotationType.isBlank() ? TYPE_COMMENT : annotationType;
    }

    /** True for typewriter text, which is drawn rather than pinned. */
    public boolean isTypewriter() {
        return TYPE_TEXT.equalsIgnoreCase(annotationTypeOrDefault());
    }

    /** True when this comment is anchored to a point on the document. */
    public boolean isAnchored() {
        return pageNumber != null && anchorX != null && anchorY != null;
    }
}
