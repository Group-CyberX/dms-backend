package com.dms.service;

import com.dms.dto.ApproverOptionDTO;
import com.dms.dao.RoleRepository;
import com.dms.dao.UserRepository;
import com.dms.models.Role;
import com.dms.models.User;
import com.dms.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, AuditLogService auditLogService,
                       NotificationService notificationService){
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public List<User> getAllUsers(){
        return userRepository.findAll();
    }

    /**
     * One page of users, with the search box and the two filters applied by the
     * database. Sorted by name so paging is stable - without an explicit sort
     * the same row can appear on two different pages.
     */
    public Page<User> searchUsers(String search, String status, String role, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "username"));

        return userRepository.search(
                normalise(search), normalise(status), normalise(role), pageable);
    }

    /** Totals across the whole directory, not just the page being shown. */
    public Map<String, Long> userStats() {
        long total = userRepository.count();
        long active = userRepository.countByStatusIgnoreCase("ACTIVE");
        return Map.of(
                "total", total,
                "active", active,
                "inactive", total - active,
                "roles", userRepository.countDistinctRoles());
    }

    /** Treats "", " " and "all" as "no filter", which is what the UI sends. */
    private String normalise(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    public List<ApproverOptionDTO> getApproverOptions() {
        return userRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .map(user -> new ApproverOptionDTO(
                        user.getUserId(),
                        user.getUsername(),
                        user.getRole() != null ? user.getRole().getName() : ""
                ))
                .collect(Collectors.toList());
    }

    public ApproverOptionDTO getCurrentUserSummary(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new ApproverOptionDTO(
                user.getUserId(),
                user.getUsername(),
                user.getRole() != null ? user.getRole().getName() : ""
        );
    }

    public User createUser(String username, String email, String password, String roleName){
        roleName = roleName.toUpperCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("ACTIVE");

        User saved = userRepository.save(user);
        // Account and role changes are the changes that alter what everyone
        // else is allowed to do, so they are always recorded.
        auditLogService.tryRecord("USER_CREATED", SecurityUtils.currentUserId(),
                saved.getUserId(), null, "SUCCESS");
        return saved;
    }

    public User updateUser(UUID userId, String username, String roleName) {
        roleName = roleName.toUpperCase();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setUsername(username);
        user.setRole(role);

        User saved = userRepository.save(user);
        auditLogService.tryRecord("USER_ROLE_CHANGED", SecurityUtils.currentUserId(),
                saved.getUserId(), null, "SUCCESS",
                "role set to " + roleName + " for " + saved.getUsername());

        // Someone's access just changed. They should hear it from the system
        // rather than discover it when a button stops working.
        notificationService.sendNotification(saved.getUserId(),
                "Your role has been changed to " + roleName + ".");
        return saved;
    }

    public User updateUserStatus(UUID userId, String status) {
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new RuntimeException("Invalid status");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(status);

        User saved = userRepository.save(user);
        boolean activated = "ACTIVE".equals(status);
        auditLogService.tryRecord(
                activated ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                SecurityUtils.currentUserId(), saved.getUserId(), null, "SUCCESS",
                saved.getUsername() + " was " + (activated ? "activated" : "deactivated"));

        notificationService.sendNotification(saved.getUserId(),
                activated
                        ? "Your account has been reactivated."
                        : "Your account has been deactivated. Contact an administrator if this is unexpected.");
        return saved;
    }
}
