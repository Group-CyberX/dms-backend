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

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getName())
        );

        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null ?
                                user.getRole().getPermissions() : "{}"
                );

        return new CustomUserDetails(
                user.getEmail(),
                user.getPasswordHash(),
                authorities,
                permissions
        );
    }
}