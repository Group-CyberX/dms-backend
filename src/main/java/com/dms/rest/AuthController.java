package com.dms.rest;

import com.dms.dto.*;
import com.dms.service.AuthService;
import jakarta.validation.Valid;
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

    // Register a new user account.
    //
    // Failures are left to GlobalExceptionHandler: it turns a constraint breach
    // into a 400 naming the offending fields, which the form shows against the
    // inputs. Catching everything here instead returned one flat string built
    // from the underlying exception, so a duplicate email surfaced to the user
    // as a database constraint name.
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request){
        return ResponseEntity.ok(authService.register(request));
    }

    // Authenticate user and generate JWT tokens.
    //
    // When sign-in codes are switched on this returns twoFactorRequired with no
    // tokens; the session is issued by /auth/verify-2fa once the emailed code
    // comes back.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
    return authService.login(request);
    }

    // Second step of a sign-in that needed a code.
    @PostMapping("/verify-2fa")
    public LoginResponse verifyTwoFactor(@Valid @RequestBody VerifyTwoFactorRequest request){
        return authService.verifyTwoFactor(request.getEmail(), request.getOtp());
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