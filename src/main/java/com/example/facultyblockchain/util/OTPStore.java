package com.example.facultyblockchain.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OTPStore {

    // Store OTPs with timestamps for each email
    private static final Map<String, OTPData> otpMap = new ConcurrentHashMap<>();

    // Inner class to hold OTP and timestamp
    private static class OTPData {
        int otp;
        long timestamp;

        OTPData(int otp, long timestamp) {
            this.otp = otp;
            this.timestamp = timestamp;
        }
    }

    // Store OTP with current timestamp
    public static void storeOTP(String email, int otp) {
        otpMap.put(email, new OTPData(otp, System.currentTimeMillis()));
    }

    // Verify OTP and check expiry (5 minutes = 300000 ms)
    public static boolean verifyOTP(String email, int otp) {
        OTPData data = otpMap.get(email);
        if (data == null) return false;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - data.timestamp;

        if (elapsedTime > 300_000) { // 5 minutes
            otpMap.remove(email); // remove expired OTP
            System.out.println("OTP expired for: " + email);
            return false;
        }

        boolean isValid = data.otp == otp;
        if (isValid) {
            otpMap.remove(email); // remove OTP after successful use
        }
        return isValid;
    }

    // Optional: clean up expired OTPs periodically (can be run by scheduler)
    public static void cleanupExpiredOTPs() {
        long now = System.currentTimeMillis();
        otpMap.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 300_000);
    }
}