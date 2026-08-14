package com.dms.service;

import com.dms.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public boolean hasPermission(Authentication auth, String permission) {

        CustomUserDetails user =
                (CustomUserDetails) auth.getPrincipal();

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