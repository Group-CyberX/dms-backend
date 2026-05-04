package com.dms.rest;

import com.dms.dto.CreateUserRequest;
import com.dms.dto.UpdateStatusRequest;
import com.dms.dto.UpdateUserRequest;
import com.dms.models.User;
import com.dms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
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
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public User updateUser(@PathVariable UUID id,
                           @RequestBody UpdateUserRequest request) {
        return userService.updateUser(
                id,
                request.getUsername(),
                request.getRole()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public User updateStatus(@PathVariable UUID id,
                             @RequestBody UpdateStatusRequest request) {
        return userService.updateUserStatus(
                id,
                request.getStatus()
        );
    }
}