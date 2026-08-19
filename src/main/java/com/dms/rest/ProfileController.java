package com.dms.rest;

import com.dms.models.User;
import com.dms.dao.UserRepository;
import com.dms.service.EmailService;
import com.dms.service.OtpService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    /** Local (0771234567) or with the country code (+94771234567). */
    private static final java.util.regex.Pattern SRI_LANKA_PHONE =
            java.util.regex.Pattern.compile("^(?:\\+94|0)(?:7\\d{8}|[1-9]\\d{8})$");

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, EmailService emailService, OtpService otpService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(user);
    }

    @PostMapping("/phone/send-otp")
    public ResponseEntity<?> sendPhoneOtp(Authentication authentication, @RequestBody Map<String, String> payload) {
        String email = authentication.getName();
        String newPhone = payload.get("newPhone");
        
        if (newPhone == null || newPhone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPhone is required"));
        }

        // Checked here too: the modal is only the first of the two places that
        // has to hold, and nothing stopped a caller storing any text at all as
        // a phone number.
        if (!SRI_LANKA_PHONE.matcher(newPhone.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Enter a Sri Lankan number, e.g. 0771234567 or +94771234567"));
        }

        newPhone = newPhone.trim();

        String otp = otpService.generateOtp(email, newPhone);
        emailService.sendOtpEmail(email, otp);
        
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/phone/verify-otp")
    public ResponseEntity<?> verifyPhoneOtp(Authentication authentication, @RequestBody Map<String, String> payload) {
        String email = authentication.getName();
        String otp = payload.get("otp");
        
        if (otp == null || otp.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "otp is required"));
        }

        boolean isValid = otpService.verifyOtp(email, otp);
        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }

        String newPhone = otpService.getPendingPhoneNumber(email);
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPhone(newPhone);
        userRepository.save(user);
        
        otpService.clearOtp(email);

        return ResponseEntity.ok(Map.of("message", "Phone number updated successfully", "phone", newPhone));
    }
    
    @PostMapping("/profile-picture")
    public ResponseEntity<?> updateProfilePicture(Authentication authentication, @RequestBody Map<String, String> payload) {
        String email = authentication.getName();
        String base64Image = payload.get("profilePicture");
        
        if (base64Image == null || base64Image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "profilePicture is required"));
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setProfilePicture(base64Image);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Profile picture updated successfully"));
    }

    @DeleteMapping("/profile-picture")
    public ResponseEntity<?> deleteProfilePicture(Authentication authentication) {
        String email = authentication.getName();
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setProfilePicture(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Profile picture removed successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody Map<String, String> payload) {
        String email = authentication.getName();
        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both current and new passwords are required"));
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Incorrect current password"));
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
