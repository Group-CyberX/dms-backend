package com.dms.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    
    private static class OtpData {
        String otp;
        LocalDateTime expiryTime;
        String newPhone;
        
        OtpData(String otp, LocalDateTime expiryTime, String newPhone) {
            this.otp = otp;
            this.expiryTime = expiryTime;
            this.newPhone = newPhone;
        }
    }

    // Stores OTP by User Email
    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public String generateOtp(String email, String newPhone) {
        String otp = String.format("%06d", random.nextInt(1000000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        otpStorage.put(email, new OtpData(otp, expiry, newPhone));
        return otp;
    }

    public boolean verifyOtp(String email, String inputOtp) {
        OtpData data = otpStorage.get(email);
        if (data == null) {
            return false;
        }
        
        if (LocalDateTime.now().isAfter(data.expiryTime)) {
            otpStorage.remove(email);
            return false;
        }
        
        if (data.otp.equals(inputOtp)) {
            return true;
        }
        
        return false;
    }
    
    public String getPendingPhoneNumber(String email) {
        OtpData data = otpStorage.get(email);
        return data != null ? data.newPhone : null;
    }

    public void clearOtp(String email) {
        otpStorage.remove(email);
    }
}
