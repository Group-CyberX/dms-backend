package com.dms.service;

import com.dms.dao.RoleRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.LoginRequest;
import com.dms.dto.RegisterRequest;
import com.dms.dto.RegisterResponse;
import com.dms.models.Role;
import com.dms.models.User;
import com.dms.security.JwtUtil;
import com.dms.dto.LoginResponse;

import com.dms.util.PermissionUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public RegisterResponse register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getFirstName() + " " + request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setPhone(request.getPhone());

        Role role = roleRepository.findByName("USER").orElseThrow();
        user.setRole(role);

        User savedUser = userRepository.save(user);
        return new RegisterResponse(
                savedUser.getUserId().toString(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isMatch = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!isMatch) {
            throw new RuntimeException("Invalid email or password");
        }

        //Generate JWT
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getName()
        );

        // Role
        String roleName = user.getRole().getName();

        // Permissions (JSON → Map)
        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null
                                ? user.getRole().getPermissions()
                                : "{}"
                );

        //Return full response
        return new LoginResponse(
                user.getEmail(),
                token,
                roleName,
                permissions
        );
    }

    public void forgotPassword(String email) {

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {

            String token = UUID.randomUUID().toString();

            user.setResetToken(token);

            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));

            userRepository.save(user);

            String resetLink = "http://localhost:3000/reset-password?token=" + token;

            System.out.println("Reset Link: " + resetLink);
        }
    }

    public void resetPassword(String token, String newPassword) {

        User user = userRepository.findByResetToken(token).orElse(null);

        if (user == null) {
            throw new RuntimeException("Invalid token");
        }

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
    }

}