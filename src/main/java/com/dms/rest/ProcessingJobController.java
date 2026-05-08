package com.dms.rest;

import com.dms.models.ProcessingJob;
import com.dms.service.ProcessingJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ProcessingJobController {

    private final ProcessingJobService processingJobService;

    public ProcessingJobController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    /**
     * Enqueue OCR job for a specific version.
     */
    @PostMapping("/ocr/{versionId}")
    public ResponseEntity<ProcessingJob> createOcrJob(@PathVariable("versionId") UUID versionId) {
        // Enqueue synchronously
        ProcessingJob job = processingJobService.enqueueJob(versionId, "OCR");

        // Fire and forget cleanly
        processingJobService.triggerOcrJobSafely(job.getJobId());

        return ResponseEntity.ok(job);
    }

    /**
     * Retrieve job status list for a given document.
     */
    @GetMapping
    public ResponseEntity<List<ProcessingJob>> getJobsByDocumentId(@RequestParam("documentId") UUID documentId) {
        List<ProcessingJob> jobs = processingJobService.getJobsForDocument(documentId);
        return ResponseEntity.ok(jobs);
    }
}
