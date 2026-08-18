package com.dms.dao;

import com.dms.models.DocumentVersions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersions, UUID> {

    @Query("select v from DocumentVersions v where v.document_id = :documentId order by v.created_at desc")
    List<DocumentVersions> findByDocument_idOrderByCreated_atDesc(@Param("documentId") UUID documentId);

    /**
     * Extracted text only, and only for versions that actually have some.
     *
     * Classification used to call findAll(), which pulls every column of every
     * version - including the full OCR text of versions with none of interest,
     * and the S3 keys and checksums it never looks at.
     */
    @Query("""
            select v.document_id as documentId, v.ocr_content as ocrContent
            from DocumentVersions v
            where v.ocr_content is not null and v.ocr_content <> ''
            """)
    List<OcrText> findOcrText();

    interface OcrText {
        UUID getDocumentId();
        String getOcrContent();
    }
}
