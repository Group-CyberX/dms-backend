package com.dms.rest;

import com.dms.dto.CreateUserRequest;
import com.dms.dto.UpdateStatusRequest;
import com.dms.dto.UpdateUserRequest;
import com.dms.models.User;
import com.dms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Unpaged list, still used by the approver and filter pickers where the
    // whole set genuinely is the answer.
    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewUser')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    /**
     * One page of the directory for the management table. Search, status and
     * role filtering all happen in the database, so the response is the size of
     * the page the user is looking at and nothing more.
     */
    @GetMapping("/page")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewUser')")
    public Page<User> getUsersPage(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String role,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return userService.searchUsers(search, status, role, page, size);
    }

    /** Directory-wide totals for the header cards, counted in the database. */
    @GetMapping("/stats")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewUser')")
    public Map<String, Long> getUserStats() {
        return userService.userStats();
    }

    @PostMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreateUser')")
    public User createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getRole()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditUser')")
    public User updateUser(@PathVariable UUID id,
                           @RequestBody UpdateUserRequest request) {
        return userService.updateUser(
                id,
                request.getUsername(),
                request.getRole()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditUser')")
    public User updateStatus(@PathVariable UUID id,
                             @RequestBody UpdateStatusRequest request) {
        return userService.updateUserStatus(
                id,
                request.getStatus()
        );
    }
}