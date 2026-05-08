package com.dms.security;

import com.dms.dao.UserRepository;
import com.dms.models.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {

    private static UserRepository staticUserRepository;

    public SecurityUtils(UserRepository userRepository) {
        // Store in a static field to allow use from static convenience methods
        SecurityUtils.staticUserRepository = userRepository;
    }

    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("Unauthenticated: no authentication present");
        }
        Object principal = auth.getPrincipal();
        String email = null;
        if (principal instanceof UserDetails) {
            email = ((UserDetails) principal).getUsername(); // in our setup, username is email
        } else if (principal instanceof String) {
            email = (String) principal;
        }
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Unable to resolve current user email from SecurityContext");
        }
        if (staticUserRepository == null) {
            throw new RuntimeException("UserRepository not initialized in SecurityUtils");
        }
        final String lookupEmail = email;
        User user = staticUserRepository.findByEmail(lookupEmail)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + lookupEmail));
        return user.getUserId();
    }
}
