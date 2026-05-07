package com.dms.service;

import com.dms.dao.DocumentVersionRepository;
import com.dms.dao.ProcessingJobRepository;
import com.dms.models.DocumentVersions;
import com.dms.models.ProcessingJob;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final MetadataExtractorService metadataExtractorService;
    private final DocumentVersionService documentVersionService; // Inject to download file

    @Autowired
    @Lazy
    private ProcessingJobService self;

    public ProcessingJobService(ProcessingJobRepository processingJobRepository,
                                DocumentVersionRepository documentVersionRepository,
                                MetadataExtractorService metadataExtractorService,
                                @Lazy DocumentVersionService documentVersionService) {
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.metadataExtractorService = metadataExtractorService;
        this.documentVersionService = documentVersionService;
    }

    /**
     * Enqueue a new job and return it immediately.
     */
    public ProcessingJob enqueueJob(UUID versionId, String jobType) {
        ProcessingJob job = new ProcessingJob(versionId, jobType, "PENDING");
        return processingJobRepository.saveAndFlush(job);
    }

    /**
     * Helper to safely trigger the Async job ONLY AFTER the current transaction commits.
     */
    public void triggerOcrJobSafely(UUID jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Start processing only after DB has physically saved the initial upload
                    self.processOcrJobAsync(jobId); // use self alias to preserve @Async proxy!
                }
            });
        } else {
            self.processOcrJobAsync(jobId);
        }
    }

    /**
     * Background processing task for an OCR Job.
     */
    @Async
    public void processOcrJobAsync(UUID jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        try {
            // Update Status to IN_PROGRESS
            job.setStatus("IN_PROGRESS");
            processingJobRepository.save(job);

            // Locate the document version
            DocumentVersions version = documentVersionRepository.findById(job.getDocumentVersionId())
                    .orElseThrow(() -> new RuntimeException("Document Version not found"));

            // 1. Fetch file content (handles both S3 and local storage)
            byte[] fileBytes = documentVersionService.getVersionFileBytes(version.getDocument_id(), version.getVersion_id());

            // Get original extension/content type
            String contentType = "application/pdf"; // A default in case parsing isn't clear
            if (version.getS3_bucket_key().toLowerCase().endsWith(".png")) contentType = "image/png";
            else if (version.getS3_bucket_key().toLowerCase().endsWith(".jpg") || version.getS3_bucket_key().toLowerCase().endsWith(".jpeg")) contentType = "image/jpeg";

            // 2. Perform OCR logic on the downloaded bytes
            String extractedText = metadataExtractorService.extractTextFromBytes(fileBytes, contentType, version.getS3_bucket_key());
            
            // 3. Set content and update document version
            version.setOcr_content(extractedText);
            documentVersionRepository.save(version);

            // Mark job as SUCCESS
            job.setStatus("SUCCESS");

        } catch (Exception e) {
            job.setStatus("FAILED");
            // optionally log or store the error message in the job entity
        } finally {
            processingJobRepository.save(job);
        }
    }

    /**
     * Retrieve jobs given a documentId, achieved by fetching all versions of the document.
     */
    public List<ProcessingJob> getJobsForDocument(UUID documentId) {
        List<DocumentVersions> versions = documentVersionRepository.findByDocument_idOrderByCreated_atDesc(documentId);
        List<UUID> versionIds = versions.stream().map(DocumentVersions::getVersion_id).toList();
        return processingJobRepository.findByDocumentVersionIdIn(versionIds);
    }
}
