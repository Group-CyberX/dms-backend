package com.dms.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The complete list of permission keys the system recognises.
 * Keys are stored verbatim in roles.permissions and checked by
 * PermissionService, so they must stay in sync with the frontend catalogue in
 * src/lib/permissions.ts.
 */
public final class PermissionCatalog {

    private static final Set<String> KEYS = new LinkedHashSet<>(Arrays.asList(
            "canViewDashboard",
            "canViewAnalyticsDashboard",

            "canViewDocument",
            "canViewAllDocuments",
            "canManageAllDocuments",
            "canCreateDocument",
            "canEditDocument",
            "canDeleteDocument",
            "canShareDocument",
            "canAssignDocument",
            "canDeleteFolder",

            "canViewSearch",
            "canAdvancedSearchSearch",
            "canSearchAllDocuments",

            "canViewTask",
            "canCreateTask",
            "canEditTask",
            "canDeleteTask",

            "canViewWorkflow",
            "canCreateWorkflow",
            "canApproveWorkflow",
            "canEditWorkflow",
            "canDeleteWorkflow",

            "canViewRecycleBin",
            "canViewAllDeletedDocuments",
            "canRestoreRecycleBin",
            "canPermanentlyDeleteRecycleBin",

            "canViewAuditLog",
            "canExportAuditLog",

            "canViewERPIntegration",
            "canConfigureERPIntegration",
            "canSyncERPIntegration",
            "canDeleteERPIntegration",

            "canViewPolicy",
            "canCreatePolicy",
            "canEditPolicy",
            "canDeletePolicy",

            "canViewUser",
            "canCreateUser",
            "canEditUser",
            "canDeleteUser",

            "canViewRole",
            "canCreateRole",
            "canEditRole",
            "canDeleteRole",

            "canViewHealthSystem",
            "canConfigureSystem",
            "canBackupSystem",
            "canRestoreSystem",

            "canViewSetting",
            "canEditSetting",
            "canManageAPIKeysSetting",
            "canManageDocumentPolicySetting",
            "canManageAccessControlSetting",
            "canExecuteDangerZoneSetting"
    ));

    private PermissionCatalog() {
    }

    public static boolean isKnown(String key) {
        return KEYS.contains(key);
    }

    public static List<String> keys() {
        return List.copyOf(KEYS);
    }
}
