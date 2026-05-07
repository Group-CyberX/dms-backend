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
import com.dms.dao.RefreshTokenRepository;
import com.dms.models.RefreshToken;

import com.dms.util.PermissionUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;    // Repository for user database operations
    private final RoleRepository roleRepository;    // Repository for role management (RBAC)
    private final PasswordEncoder passwordEncoder;  // Used for hashing passwords securely
    private final JwtUtil jwtUtil;                  // Utility class for generating and validating JWT tokens
    private final RefreshTokenRepository refreshTokenRepository;    // Repository to store and manage refresh tokens

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
    }
    // Register
    public RegisterResponse register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getFirstName() + " " + request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setPhone(request.getPhone());

        Role role = roleRepository.findByName("USER")
                .orElseThrow(()-> new RuntimeException("Default Role Not Found"));
        user.setRole(role);

        User savedUser = userRepository.save(user);
        return new RegisterResponse(
                savedUser.getUserId().toString(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );
    }
    //Login
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isMatch = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash());

        if (!isMatch) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate access token
        String accessToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getName()
        );

        // Generate refresh token
        String refreshToken = createRefreshToken(user);

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
                accessToken,
                refreshToken,
                roleName,
                permissions
        );
    }
//Forget password
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
//reset password
    public void resetPassword(String token, String newPassword) {

        User user = userRepository.findByResetToken(token).orElse(null);

        if (user == null) {
            throw new RuntimeException("Invalid token");
        }

        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }


        user.setPasswordHash(passwordEncoder.encode(newPassword));

        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
    }

    //Refresh token
    private String createRefreshToken(User user) {

        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public LoginResponse refresh(String requestToken) {

        RefreshToken token = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Check revoked
        if (token.isRevoked()) {
            throw new RuntimeException("Token revoked");
        }

        // Check expiry
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        User user = token.getUser();

        // Generate new access token
        String newAccessToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getName()
        );

        String roleName = user.getRole().getName();

        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null
                                ? user.getRole().getPermissions()
                                : "{}"
                );

        return new LoginResponse(
                user.getEmail(),
                newAccessToken,
                requestToken,
                roleName,
                permissions
        );
    }
//refresh token revoke
    public void logout(String requestToken) {

        RefreshToken token = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Revoke the refresh token to invalidate a session
        token.setRevoked(true);

        refreshTokenRepository.save(token);
    }
}