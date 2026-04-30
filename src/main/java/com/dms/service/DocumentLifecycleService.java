package com.dms.service;

import org.springframework.stereotype.Service;

@Service
public class DocumentLifecycleService {

    // Temporary implementation for updating document status.In real system, this will update document table in DB
    public void updateDocumentStatus(String documentId, String status) {
        System.out.println("DocumentLifecycleService -> documentId: " + documentId + ", status: " + status);
    }
}