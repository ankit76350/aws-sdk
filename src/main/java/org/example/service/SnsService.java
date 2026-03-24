package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnsService {

    private final SnsClient snsClient;

    // Stores OTP entries: phoneNumber -> OtpEntry
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    private static final int OTP_EXPIRY_SECONDS = 300; // 5 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    public String sendOtp(String phoneNumber) {
        String otp = generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(OTP_EXPIRY_SECONDS);
        otpStore.put(phoneNumber, new OtpEntry(otp, expiresAt));

        PublishRequest request = PublishRequest.builder()
                .phoneNumber(phoneNumber)
                .message("Your OTP is: " + otp + ". It is valid for 5 minutes.")
                .build();

        PublishResponse response = snsClient.publish(request);
        log.info("OTP sent to {} | messageId={}", phoneNumber, response.messageId());
        return response.messageId();
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        OtpEntry entry = otpStore.get(phoneNumber);
        if (entry == null) {
            log.warn("No OTP found for phone={}", phoneNumber);
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(phoneNumber);
            log.warn("OTP expired for phone={}", phoneNumber);
            return false;
        }
        boolean matched = entry.otp().equals(otp);
        if (matched) {
            otpStore.remove(phoneNumber); // invalidate after successful verification
        }
        return matched;
    }

    private String generateOtp() {
        int code = 100000 + RANDOM.nextInt(900000); // 6-digit OTP
        return String.valueOf(code);
    }

    private record OtpEntry(String otp, Instant expiresAt) {}
}
