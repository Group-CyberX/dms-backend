package com.dms.config;

import com.dms.dao.RoleRepository;
import com.dms.models.Role;
import com.dms.security.PermissionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Gives the six built-in roles a coherent permission map.
 *
 * The permission maps had drifted badly from what the interface expects. The
 * frontend decides what a role can see from these keys alone
 * (see access-control.ts: once a role has any explicit permissions, anything
 * not listed is denied outright), so a role missing a single visibility key
 * loses a whole screen. In practice APPROVER held one key that no screen or
 * endpoint consults, which left it with an empty sidebar and every page
 * bouncing to /unauthorized; AUDITOR could not open the dashboard yet could
 * back up and restore the system.
 *
 * Each baseline below is the set of keys the role's job actually needs, taken
 * from the role descriptions in the requirements document. Anything not listed
 * is written as false rather than omitted, so what a role can do is visible in
 * one place instead of depending on which keys happen to be absent.
 *
 * Roles created by hand through Role Management are never touched. Set
 * {@code app.security.normalize-builtin-roles=false} to leave the built-ins
 * alone as well, if they are being tuned by hand.
 */
@Component
public class RolePermissionSeeder {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionSeeder.class);

    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.security.normalize-builtin-roles:true}")
    private boolean enabled;

    public RolePermissionSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** Role name -> the permission keys that role should hold. */
    private static final Map<String, List<String>> BASELINES = Map.of(
            "SYSTEM_ADMIN", PermissionCatalog.keys(),

            // Document policies, retention and compliance across the library.
            "DOCUMENT_ADMIN", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canViewAllDocuments", "canCreateDocument", "canEditDocument",
                    "canDeleteDocument", "canShareDocument", "canManageAllDocuments",
                    "canViewSearch", "canAdvancedSearchSearch", "canSearchAllDocuments",
                    "canViewTask",
                    "canViewWorkflow",
                    "canViewRecycleBin", "canViewAllDeletedDocuments",
                    "canRestoreRecycleBin", "canPermanentlyDeleteRecycleBin",
                    "canViewAuditLog", "canExportAuditLog",
                    "canViewPolicy", "canCreatePolicy", "canEditPolicy", "canDeletePolicy",
                    "canViewSetting", "canEditSetting", "canManageDocumentPolicySetting"),

            // Compliance monitoring and audit review: reads everything relevant,
            // changes nothing. Notably no backup/restore/configure - an auditor
            // who can alter the system cannot credibly audit it.
            "AUDITOR", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canViewAllDocuments",
                    "canViewSearch", "canAdvancedSearchSearch", "canSearchAllDocuments",
                    "canViewWorkflow",
                    "canViewAuditLog", "canExportAuditLog",
                    "canViewERPIntegration",
                    "canViewPolicy",
                    "canViewSetting"),

            // Designs workflows and approval chains, so it needs to list users
            // in order to choose approvers, and to see the uploads waiting to be
            // routed rather than only its own.
            "BUSINESS_PROCESS_OWNER", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canViewAllDocuments", "canCreateDocument",
                    "canEditDocument", "canShareDocument",
                    "canViewSearch", "canAdvancedSearchSearch", "canSearchAllDocuments",
                    "canViewTask", "canCreateTask", "canEditTask", "canDeleteTask",
                    "canViewWorkflow", "canCreateWorkflow", "canApproveWorkflow",
                    "canEditWorkflow", "canDeleteWorkflow",
                    "canViewAuditLog",
                    "canViewUser",
                    "canViewSetting", "canEditSetting"),

            // Reviews and signs what is assigned to them. Signing stamps the PDF
            // and stores it as a new version, which is why editing is included.
            "APPROVER", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canEditDocument",
                    "canViewSearch", "canAdvancedSearchSearch",
                    "canViewTask", "canEditTask",
                    "canViewWorkflow", "canApproveWorkflow",
                    "canViewSetting", "canEditSetting"),

            // Everyday use: create and collaborate. Uploading is where this role's
            // part ends - the approval chain is chosen by whoever triages the
            // upload, so it can follow a workflow but not start one.
            "USER", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canCreateDocument", "canEditDocument",
                    "canDeleteDocument", "canShareDocument",
                    "canViewSearch", "canAdvancedSearchSearch",
                    "canViewTask",
                    "canViewWorkflow",
                    "canViewRecycleBin", "canRestoreRecycleBin",
                    "canViewSetting", "canEditSetting")
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            log.info("Built-in role normalisation is switched off "
                    + "(app.security.normalize-builtin-roles=false).");
            return;
        }

        for (Role role : roleRepository.findAll()) {
            List<String> granted = BASELINES.get(role.getName());
            if (granted == null) {
                continue; // a custom role - its permissions belong to whoever made it
            }

            try {
                Map<String, Boolean> desired = new LinkedHashMap<>();
                Set<String> grantedSet = new HashSet<>(granted);
                for (String key : PermissionCatalog.keys()) {
                    desired.put(key, grantedSet.contains(key));
                }

                Map<String, Boolean> current = readPermissions(role.getPermissions());
                if (desired.equals(current)) {
                    continue; // already correct - stay silent
                }

                List<String> added = desired.entrySet().stream()
                        .filter(e -> e.getValue() && !Boolean.TRUE.equals(current.get(e.getKey())))
                        .map(Map.Entry::getKey).sorted().toList();
                List<String> removed = current.entrySet().stream()
                        .filter(e -> Boolean.TRUE.equals(e.getValue())
                                && !Boolean.TRUE.equals(desired.get(e.getKey())))
                        .map(Map.Entry::getKey).sorted().toList();

                role.setPermissions(objectMapper.writeValueAsString(desired));
                roleRepository.save(role);

                log.info("Role {}: granted {}, revoked {}.", role.getName(),
                        added.isEmpty() ? "nothing" : added,
                        removed.isEmpty() ? "nothing" : removed);
            } catch (Exception e) {
                log.error("Could not normalise permissions for role {}: {}",
                        role.getName(), e.getMessage());
            }
        }
    }

    private Map<String, Boolean> readPermissions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            Map<String, Boolean> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(String.valueOf(k), Boolean.TRUE.equals(v)));
            return result;
        } catch (Exception e) {
            // Unreadable JSON is treated as "no permissions", which the baseline
            // then replaces wholesale - the desired outcome either way.
            return Map.of();
        }
    }
}
