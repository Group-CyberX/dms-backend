package com.dms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Map;

public class CustomUserDetails implements UserDetails {

    /** Account states that exist in the users.status enum. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Boolean> permissions;
    private final String status;

    public CustomUserDetails(String username,
                             String password,
                             Collection<? extends GrantedAuthority> authorities,
                             Map<String, Boolean> permissions,
                             String status) {
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.permissions = permissions;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    /** A suspended account is treated as locked. */
    @Override
    public boolean isAccountNonLocked() {
        return !STATUS_SUSPENDED.equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /**
     * Only an ACTIVE account may be used.
     *
     * These four flags all returned true regardless of the account's state,
     * which meant deactivating a user in the admin screen had no effect on
     * whether they could sign in or keep using an existing session.
     */
    @Override
    public boolean isEnabled() {
        return status == null || STATUS_ACTIVE.equalsIgnoreCase(status);
    }
}