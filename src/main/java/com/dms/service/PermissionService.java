package com.dms.service;

import com.dms.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public boolean hasPermission(Authentication auth, String permission) {

        // An anonymous or otherwise unrecognised principal simply has no
        // permissions. Casting it blindly threw a ClassCastException, which
        // Spring surfaced as 500 - so a missing token looked like a server bug
        // instead of an access denial.
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user)) {
            return false;
        }

        // System admins can do everything
        boolean isSystemAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
        
        if (isSystemAdmin) {
            return true;
        }

        return user.getPermissions()
                .getOrDefault(permission, false);
    }
}