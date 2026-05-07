package com.dms.service;

import com.dms.dto.ApproverOptionDTO;
import com.dms.dao.RoleRepository;
import com.dms.dao.UserRepository;
import com.dms.models.Role;
import com.dms.models.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }
    public List<User> getAllUsers(){
        return userRepository.findAll();
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

    public User createUser(String username,String email,String password,String roleName){
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

        return userRepository.save(user);
    }
    public User updateUser(UUID userId, String username, String roleName) {
        roleName = roleName.toUpperCase();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setUsername(username);
        user.setRole(role);

        return userRepository.save(user);
    }
    public User updateUserStatus(UUID userId, String status) {
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new RuntimeException("Invalid status");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(status);

        return userRepository.save(user);
    }
}
