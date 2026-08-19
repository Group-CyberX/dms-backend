package com.dms.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Six-digit codes emailed to confirm something.
 *
 * Two things use this and they must not overwrite each other: confirming a new
 * phone number, and confirming a sign-in. Both are keyed by the same email
 * address, so the store is scoped by purpose - otherwise starting a phone
 * change would silently invalidate a sign-in code the person was still typing.
 */
@Service
public class OtpService {

    /** What a code was issued for. */
    public static final String PURPOSE_PHONE = "PHONE";
    public static final String PURPOSE_LOGIN = "LOGIN";

    private static final int VALID_FOR_MINUTES = 15;

    private static class OtpData {
        final String otp;
        final LocalDateTime expiryTime;
        final String newPhone;

        OtpData(String otp, LocalDateTime expiryTime, String newPhone) {
            this.otp = otp;
            this.expiryTime = expiryTime;
            this.newPhone = newPhone;
        }
    }

    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();
    // A predictable code is not a second factor, so this is not java.util.Random.
    private final SecureRandom random = new SecureRandom();

    private String keyFor(String purpose, String email) {
        return purpose + "::" + (email == null ? "" : email.toLowerCase());
    }

    private String issue(String purpose, String email, String newPhone) {
        String otp = String.format("%06d", random.nextInt(1000000));
        otpStorage.put(keyFor(purpose, email),
                new OtpData(otp, LocalDateTime.now().plusMinutes(VALID_FOR_MINUTES), newPhone));
        return otp;
    }

    private boolean check(String purpose, String email, String inputOtp) {
        String key = keyFor(purpose, email);
        OtpData data = otpStorage.get(key);
        if (data == null || inputOtp == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(data.expiryTime)) {
            otpStorage.remove(key);
            return false;
        }

        return data.otp.equals(inputOtp.trim());
    }

    // ---- phone number change -------------------------------------------

    public String generateOtp(String email, String newPhone) {
        return issue(PURPOSE_PHONE, email, newPhone);
    }

    public boolean verifyOtp(String email, String inputOtp) {
        return check(PURPOSE_PHONE, email, inputOtp);
    }

    public String getPendingPhoneNumber(String email) {
        OtpData data = otpStorage.get(keyFor(PURPOSE_PHONE, email));
        return data != null ? data.newPhone : null;
    }

    public void clearOtp(String email) {
        otpStorage.remove(keyFor(PURPOSE_PHONE, email));
    }

    // ---- sign-in --------------------------------------------------------

    public String generateLoginOtp(String email) {
        return issue(PURPOSE_LOGIN, email, null);
    }

    public boolean verifyLoginOtp(String email, String inputOtp) {
        return check(PURPOSE_LOGIN, email, inputOtp);
    }

    public void clearLoginOtp(String email) {
        otpStorage.remove(keyFor(PURPOSE_LOGIN, email));
    }
}
