package com.dms.service;

import com.dms.dao.SettingEntryRepository;
import com.dms.models.SettingEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Stores the choices made on the Settings screen.
 *
 * Two scopes: preferences that belong to one person (US-72's language and
 * theme, US-73's notification switches) and organisation-wide configuration
 * that only an administrator may change. Both are persisted - the screen
 * previously kept everything in component state and reported success without
 * writing anything, so every choice was lost on reload.
 *
 * Unknown keys are dropped on the way in. That keeps the payload a settings
 * document rather than arbitrary per-user storage, and means a renamed option
 * disappears instead of lingering forever.
 */
@Service
public class SettingsService {

    /** Preferences a user may set for themselves. */
    private static final Set<String> USER_KEYS = Set.of(
            "emailNotifications", "pushNotifications", "documentApproval",
            "workflowUpdates", "systemAlerts",
            "language", "timezone", "dateFormat",
            "theme");

    /** Organisation-wide configuration, editable by administrators. */
    private static final Set<String> ORG_KEYS = Set.of(
            "defaultRetentionDays", "recycleBinRetentionDays",
            "automaticVersionControl", "maxVersionsPerDocument", "mandatoryClassification",
            "sessionTimeout", "passwordPolicy", "passwordExpiry", "allowedFileTypes");

    /** Applied when a user has never saved anything. */
    private static final Map<String, Object> USER_DEFAULTS = Map.ofEntries(
            Map.entry("emailNotifications", true),
            Map.entry("pushNotifications", false),
            Map.entry("documentApproval", true),
            Map.entry("workflowUpdates", true),
            Map.entry("systemAlerts", false),
            Map.entry("language", "English"),
            Map.entry("timezone", "UTC+5:30 (Sri Lanka)"),
            Map.entry("dateFormat", "DD/MM/YYYY"),
            Map.entry("theme", "Light"));

    private static final Map<String, Object> ORG_DEFAULTS = Map.ofEntries(
            Map.entry("defaultRetentionDays", "2555 Days"),
            Map.entry("recycleBinRetentionDays", "30 Days"),
            Map.entry("automaticVersionControl", true),
            Map.entry("maxVersionsPerDocument", 10),
            Map.entry("mandatoryClassification", false),
            Map.entry("sessionTimeout", "30 Minutes"),
            Map.entry("passwordPolicy", "Strong (8+ chars, mixed, numbers, symbols)"),
            Map.entry("passwordExpiry", "90 Days"),
            Map.entry("allowedFileTypes", "PDF, DOC, DOCX, XLS, XLSX, JPG, PNG"));

    private final SettingEntryRepository repository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SettingsService(SettingEntryRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userPreferences(UUID userId) {
        Map<String, Object> merged = new LinkedHashMap<>(USER_DEFAULTS);
        repository.findByScopeAndOwnerId(SettingEntry.SCOPE_USER, userId)
                .ifPresent(entry -> merged.putAll(read(entry.getPayload())));
        return merged;
    }

    @Transactional
    public Map<String, Object> saveUserPreferences(UUID userId, Map<String, Object> incoming, String actorIp) {
        Map<String, Object> current = userPreferences(userId);
        current.putAll(filter(incoming, USER_KEYS));

        SettingEntry entry = repository.findByScopeAndOwnerId(SettingEntry.SCOPE_USER, userId)
                .orElseGet(() -> {
                    SettingEntry fresh = new SettingEntry();
                    fresh.setScope(SettingEntry.SCOPE_USER);
                    fresh.setOwnerId(userId);
                    return fresh;
                });
        entry.setPayload(write(current));
        entry.setUpdatedAt(LocalDateTime.now());
        repository.save(entry);

        auditLogService.createAuditLog("SETTINGS_UPDATED", userId, actorIp, "SUCCESS");
        return current;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> organisationSettings() {
        Map<String, Object> merged = new LinkedHashMap<>(ORG_DEFAULTS);
        repository.findFirstByScope(SettingEntry.SCOPE_ORG)
                .ifPresent(entry -> merged.putAll(read(entry.getPayload())));
        return merged;
    }

    @Transactional
    public Map<String, Object> saveOrganisationSettings(Map<String, Object> incoming, UUID actorId, String actorIp) {
        Map<String, Object> current = organisationSettings();
        current.putAll(filter(incoming, ORG_KEYS));

        SettingEntry entry = repository.findFirstByScope(SettingEntry.SCOPE_ORG)
                .orElseGet(() -> {
                    SettingEntry fresh = new SettingEntry();
                    fresh.setScope(SettingEntry.SCOPE_ORG);
                    fresh.setOwnerId(null);
                    return fresh;
                });
        entry.setPayload(write(current));
        entry.setUpdatedAt(LocalDateTime.now());
        repository.save(entry);

        auditLogService.createAuditLog("ORG_SETTINGS_UPDATED", actorId, actorIp, "SUCCESS");
        return current;
    }

    // ------------------------------------------------------------------

    private Map<String, Object> filter(Map<String, Object> incoming, Set<String> allowed) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (incoming == null) {
            return result;
        }
        incoming.forEach((key, value) -> {
            if (allowed.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise settings", e);
        }
    }
}
