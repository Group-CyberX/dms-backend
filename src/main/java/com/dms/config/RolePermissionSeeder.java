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
                    "canViewDocument", "canViewAllDocuments", "canManageAllDocuments", "canCreateDocument", "canEditDocument",
                    "canDeleteDocument", "canShareDocument", "canAssignDocument",
                    "canDeleteFolder",
                    "canViewSearch", "canAdvancedSearchSearch", "canSearchAllDocuments",
                    "canViewTask",
                    "canViewWorkflow",
                    "canViewRecycleBin", "canViewAllDeletedDocuments", "canRestoreRecycleBin", "canPermanentlyDeleteRecycleBin",
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
            // in order to choose approvers.
            "BUSINESS_PROCESS_OWNER", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canViewAllDocuments", "canCreateDocument", "canEditDocument",
                    "canShareDocument", "canAssignDocument",
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

            // Everyday use: create, collaborate, submit for approval.
            "USER", List.of(
                    "canViewDashboard", "canViewAnalyticsDashboard",
                    "canViewDocument", "canCreateDocument", "canEditDocument",
                    "canDeleteDocument", "canShareDocument",
                    "canViewSearch", "canAdvancedSearchSearch",
                    "canViewTask",
                    "canViewWorkflow", "canCreateWorkflow",
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
                Map<String, Boolean> current = readPermissions(role.getPermissions());
                Set<String> grantedSet = new HashSet<>(granted);

                // A key the role already carries was decided by whoever last
                // saved it in Role Management, and is left exactly as they set
                // it. Only keys it has never seen take the baseline value -
                // otherwise turning a permission off there lasted until the
                // next restart, when this class turned it back on.
                Map<String, Boolean> merged = new LinkedHashMap<>();
                List<String> newlyGranted = new ArrayList<>();
                int filledIn = 0;
                for (String key : PermissionCatalog.keys()) {
                    if (current.containsKey(key)) {
                        merged.put(key, Boolean.TRUE.equals(current.get(key)));
                    } else {
                        boolean granted2 = grantedSet.contains(key);
                        merged.put(key, granted2);
                        filledIn++;
                        if (granted2) {
                            newlyGranted.add(key);
                        }
                    }
                }

                if (merged.equals(current)) {
                    continue; // nothing new to fill in - stay silent
                }

                List<String> dropped = current.keySet().stream()
                        .filter(key -> !merged.containsKey(key))
                        .sorted().toList();

                role.setPermissions(objectMapper.writeValueAsString(merged));
                roleRepository.save(role);

                log.info("Role {}: {} new key(s), granted {}{}.", role.getName(), filledIn,
                        newlyGranted.isEmpty() ? "none" : newlyGranted,
                        dropped.isEmpty() ? "" : ", dropped unknown " + dropped);
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
