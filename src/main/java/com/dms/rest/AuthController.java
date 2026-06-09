package com.dms.rest;

import com.dms.dto.*;
import com.dms.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


// Controller responsible for handling authentication-related API requests
@RestController
@RequestMapping("/auth")
public class AuthController {

    // Service layer handling business logic for authentication
    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    // Register a new user account
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request){
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Registration failed: " + e.getMessage());
        }
    }

    // Authenticate user and generate JWT tokens
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){
    return authService.login(request);
    }

    // Initiate password reset process by sending reset toke
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestBody ForgotPasswordRequest request) {

    authService.forgotPassword(request.getEmail());

    return "Reset link sent";
    }

    // Reset user password using token
    @PostMapping("/reset-password")
    public String resetPassword(@RequestBody ResetPasswordRequest request) {

    authService.resetPassword(request.getToken(), request.getNewPassword());

    return "Password reset successful";
    }

    // Validate reset token and return expiry info for frontend countdown
    @GetMapping("/validate-token")
    public ResponseEntity<ValidateTokenResponse> validateToken(@RequestParam String token) {
        ValidateTokenResponse response = authService.validateResetToken(token);
        return ResponseEntity.ok(response);
    }

    // Generate a new access token using a refresh token
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {

        return ResponseEntity.ok(
                authService.refresh(request.getRefreshToken())
        );
    }

    // Logout user by invalidating the refresh token
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestBody RefreshTokenRequest request) {

        authService.logout(request.getRefreshToken());

        return ResponseEntity.ok("Logged out successfully");
    }
}