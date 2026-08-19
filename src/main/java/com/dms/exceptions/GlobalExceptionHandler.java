package com.dms.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
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

    // email already taken -> 400, in the same shape as a validation failure so
    // the registration form can show it under the email input rather than as a
    // detached banner.
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<?> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("errors", Map.of("email", ex.getMessage()));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // name already taken -> 400, keyed to the fields the name is built from.
    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<?> handleUsernameTaken(UsernameTakenException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("errors", Map.of(
                "firstName", ex.getMessage(),
                "lastName", ex.getMessage()));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * A unique or foreign key the caller broke that nothing above caught.
     *
     * The catch-all below would answer this with a 500 whose body is the raw
     * SQL and constraint name - both useless to the person reading it and more
     * than they should be told about the schema. This keeps it a 400 with a
     * plain sentence, and leaves the detail in the log.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {

        ex.printStackTrace();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "That conflicts with something already saved. Check the details and try again.");

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // failed authentication -> 401, not 500
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // deactivated or suspended account -> 403, with a message the sign-in
    // screen can show as-is. Distinct from 401 so the client does not treat it
    // as a wrong password and offer a retry.
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<?> handleDisabledAccount(DisabledException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
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