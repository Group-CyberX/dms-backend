package com.dms.service;

import com.dms.dao.RoleRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.ValidateTokenResponse;
import com.dms.dto.LoginRequest;
import com.dms.dto.RegisterRequest;
import com.dms.dto.RegisterResponse;
import com.dms.models.Role;
import com.dms.models.User;
import com.dms.security.JwtUtil;
import com.dms.dto.LoginResponse;
import com.dms.dao.RefreshTokenRepository;
import com.dms.models.RefreshToken;

import com.dms.exceptions.EmailAlreadyRegisteredException;
import com.dms.exceptions.UsernameTakenException;
import com.dms.util.PermissionUtil;
import com.dms.security.CustomUserDetails;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
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
    private final EmailService emailService;        // Service for sending emails
    private final AuditLogService auditLogService;  // Records who signed in, and who failed to
    private final NotificationService notificationService;  // Security alerts to the account owner
    private final OtpService otpService;                    // Six-digit sign-in codes
    private final SettingsService settingsService;          // Whether sign-in codes are required

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RefreshTokenRepository refreshTokenRepository,
                       EmailService emailService,
                       AuditLogService auditLogService,
                       NotificationService notificationService,
                       OtpService otpService,
                       SettingsService settingsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.otpService = otpService;
        this.settingsService = settingsService;
    }
    // Register
    public RegisterResponse register(RegisterRequest request) {
        // Both of these are unique in the database. Checking them here means a
        // clash comes back naming the field that caused it, instead of the
        // constraint violation surfacing as an error the reader can do nothing
        // with.
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyRegisteredException("An account with this email already exists");
        }

        String username = request.getFirstName() + " " + request.getLastName();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UsernameTakenException("Someone is already registered under this name");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setPhone(request.getPhone());

        Role role = roleRepository.findByName("USER")
                .orElseThrow(()-> new RuntimeException("Default Role Not Found"));
        user.setRole(role);

        User savedUser = userRepository.save(user);
        auditLogService.tryRecord("USER_REGISTERED", savedUser.getUserId(), savedUser.getUserId(), null, "SUCCESS");

        return new RegisterResponse(
                savedUser.getUserId().toString(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );
    }
    //Login
    /**
     * Rejects any account that is not ACTIVE, recording the attempt.
     *
     * INACTIVE covers a deactivated ex-employee, SUSPENDED a temporarily
     * blocked account; neither may hold a session.
     */
    private void requireActive(User user) {
        String status = user.getStatus();
        if (status == null || CustomUserDetails.STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return;
        }

        auditLogService.tryRecord("LOGIN_BLOCKED_INACTIVE", user.getUserId(), null, null, "FAILED");
        throw new DisabledException("This account is not active. Contact your administrator.");
    }

    public LoginResponse login(LoginRequest request) {

        // Same message for "no such user" and "wrong password" so the endpoint
        // cannot be used to discover which email addresses are registered.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    // Recorded against no user, because there is no user - but
                    // the attempt itself is what an auditor needs to see.
                    auditLogService.tryRecord("LOGIN_FAILED", null, null, null, "FAILED");
                    return new BadCredentialsException("Invalid email or password");
                });

        boolean isMatch = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash());

        if (!isMatch) {
            auditLogService.tryRecord("LOGIN_FAILED", user.getUserId(), null, null, "FAILED");
            throw new BadCredentialsException("Invalid email or password");
        }

        // Only an active account may sign in. Login authenticates by comparing
        // the hash directly rather than going through the AuthenticationManager,
        // so the UserDetails account flags are never consulted here and the
        // check has to be explicit - without it, deactivating a user in the
        // admin screen did not stop them signing in.
        //
        // Checked after the password, so the distinct message cannot be used to
        // discover which accounts exist.
        requireActive(user);

        // With sign-in codes switched on, the password alone does not open a
        // session: a code goes to the address on the account and the session is
        // issued only once it comes back. Read as a single setting rather than
        // the whole settings document, so an ordinary sign-in costs one small
        // query more and nothing else.
        // A code is needed when the organisation requires one of everybody, or
        // when this person has switched it on for their own account. The
        // per-user lookup is skipped whenever the organisation-wide switch has
        // already decided it.
        boolean needsCode = settingsService.twoFactorRequired()
                || settingsService.twoFactorEnabledFor(user.getUserId());

        if (needsCode) {
            String otp = otpService.generateLoginOtp(user.getEmail());
            try {
                emailService.sendOtpEmail(user.getEmail(), otp);
            } catch (Exception e) {
                // Without the code nobody could finish signing in, so this must
                // not look like a wrong password.
                otpService.clearLoginOtp(user.getEmail());
                throw new IllegalStateException(
                        "Could not send your verification code. Try again shortly.");
            }

            auditLogService.tryRecord("LOGIN_2FA_SENT", user.getUserId(), null, null, "SUCCESS");
            return LoginResponse.pendingTwoFactor(user.getEmail());
        }

        return issueSession(user);
    }

    /**
     * Confirms a sign-in code and opens the session.
     *
     * The password was already checked by login(); this step only proves the
     * person also reads the account's mailbox. The code is cleared on success
     * so it cannot be replayed.
     */
    public LoginResponse verifyTwoFactor(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired code"));

        if (!otpService.verifyLoginOtp(email, otp)) {
            auditLogService.tryRecord("LOGIN_2FA_FAILED", user.getUserId(), null, null, "FAILED");
            throw new BadCredentialsException("Invalid or expired code");
        }

        // Re-checked here: an account can be deactivated between the password
        // and the code.
        requireActive(user);

        otpService.clearLoginOtp(email);
        return issueSession(user);
    }

    /** Issues the tokens for an already-authenticated user. */
    private LoginResponse issueSession(User user) {
        String accessToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getName()
        );

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.tryRecord("LOGIN_SUCCESS", user.getUserId(), null, null, "SUCCESS");

        String refreshToken = createRefreshToken(user);
        String roleName = user.getRole().getName();

        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null
                                ? user.getRole().getPermissions()
                                : "{}"
                );

        return new LoginResponse(
                user.getEmail(),
                user.getUsername(),
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

            emailService.sendPasswordResetEmail(email, token);

            // A reset request is an account-security event whether or not it was
            // the real owner who asked for it - which is precisely why both the
            // trail and the owner need to hear about it.
            auditLogService.tryRecord("PASSWORD_RESET_REQUESTED", user.getUserId(),
                    user.getUserId(), null, "SUCCESS", "a password reset link was sent");
            notificationService.sendNotification(user.getUserId(),
                    "A password reset link was requested for your account. If this was not you, contact an administrator.");
        }
        // A request for an unknown address is deliberately silent to the caller
        // - saying "no such user" would let anyone test which emails exist - but
        // it is still worth recording that someone tried.
        else {
            auditLogService.tryRecord("PASSWORD_RESET_REQUESTED", null, null, null,
                    "FAILED", "reset requested for an address with no account");
        }
    }
//reset password
    public void resetPassword(String token, String newPassword) {

        User user = userRepository.findByResetToken(token).orElse(null);

        if (user == null) {
            auditLogService.tryRecord("PASSWORD_RESET_FAILED", null, null, null,
                    "FAILED", "reset attempted with an unrecognised token");
            throw new IllegalStateException("This reset link is not valid.");
        }

        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            auditLogService.tryRecord("PASSWORD_RESET_FAILED", user.getUserId(), user.getUserId(),
                    null, "FAILED", "reset attempted with an expired token");
            throw new IllegalStateException("This reset link has expired. Request a new one.");
        }


        user.setPasswordHash(passwordEncoder.encode(newPassword));

        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        auditLogService.tryRecord("PASSWORD_CHANGED", user.getUserId(), user.getUserId(),
                null, "SUCCESS", "password changed using a reset link");
        notificationService.sendNotification(user.getUserId(),
                "Your password was changed. If this was not you, contact an administrator immediately.");

        userRepository.save(user);
    }

    // Validate reset token and return expiry info for frontend countdown
    public ValidateTokenResponse validateResetToken(String token) {

        User user = userRepository.findByResetToken(token).orElse(null);

        if (user == null) {
            return new ValidateTokenResponse(false, null, "Invalid or expired reset link");
        }

        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return new ValidateTokenResponse(false, null, "Reset link has expired");
        }

        return new ValidateTokenResponse(
                true,
                user.getResetTokenExpiry().toString(),
                "Token is valid"
        );
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

        // Deactivating an account must also end the sessions it already has,
        // otherwise a held refresh token keeps minting access tokens.
        requireActive(user);

        // Generate new access token
        String newAccessToken = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getName()
        );

        // Token rotation: revoke old refresh token and issue new one
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        String newRefreshToken = createRefreshToken(user);

        String roleName = user.getRole().getName();

        Map<String, Boolean> permissions =
                PermissionUtil.parsePermissions(
                        user.getRole().getPermissions() != null
                                ? user.getRole().getPermissions()
                                : "{}"
                );

        return new LoginResponse(
                user.getEmail(),
                user.getUsername(),
                newAccessToken,
                newRefreshToken,
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

        auditLogService.tryRecord("LOGOUT",
                token.getUser() != null ? token.getUser().getUserId() : null,
                null, null, "SUCCESS");
    }
}