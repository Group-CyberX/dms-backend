package com.dms.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final com.dms.service.AuditLogService auditLogService;

    public GlobalExceptionHandler(com.dms.service.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // failed authentication -> 401, not 500
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // someone else is editing the document -> 409, with who is holding it
    @ExceptionHandler(DocumentLockedException.class)
    public ResponseEntity<?> handleDocumentLocked(DocumentLockedException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("lockedByUsername", ex.getLockedByUsername());
        response.put("lockedAt", ex.getLockedAt());

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * The caller asked for something the current state does not allow - a
     * revoked link, a document with no stored version, a file that is not a PDF.
     *
     * These are the caller's problem to fix, not a server fault, so they return
     * 400 with the explanation rather than a 500 that reads like a crash.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Access denied must stay a 403; the catch-all below would make it a 500.
     *
     * It is also recorded. A trail that only contains what people were allowed
     * to do answers half the question - "who tried to reach what they should
     * not" is the half an auditor actually asks about, and nothing was
     * capturing it.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            jakarta.servlet.http.HttpServletRequest request) {

        auditLogService.tryRecordCurrentUser(
                "PERMISSION_DENIED",
                null,
                request.getRemoteAddr(),
                "FAILED",
                request.getMethod() + " " + request.getRequestURI());

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // general runtime errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        ex.printStackTrace(); // ADDED LOG FOR EVERYTHING

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}