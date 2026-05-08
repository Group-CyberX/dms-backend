package com.dms.security;

import com.dms.util.PermissionUtil;
import java.util.Map;
import com.dms.dao.UserRepository;
import com.dms.models.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// Service used by Spring Security to load user details during authentication
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Fetch user details from database using email
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Retrieve user from database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));


        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getName())
        );

        // Parse permissions JSON into map
        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null ?
                                user.getRole().getPermissions() : "{}"
                );

        // Return custom user details object
        return new CustomUserDetails(
                user.getEmail(),
                user.getPasswordHash(),
                authorities,
                permissions
        );
    }
}