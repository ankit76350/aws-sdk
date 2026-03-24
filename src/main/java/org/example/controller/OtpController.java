package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.OtpRequest;
import org.example.dto.OtpVerifyRequest;
import org.example.service.SnsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sns")
@RequiredArgsConstructor
public class OtpController {

    private final SnsService snsService;

    /**
     * POST /sns/otp/send
     * Body: { "phoneNumber": "+919876543210" }
     * Sends a 6-digit OTP to the given mobile number via AWS SNS SMS.
     */
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@RequestBody OtpRequest request) {
        String messageId = snsService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.ok("OTP sent successfully. messageId=" + messageId, null));
    }

    /**
     * POST /sns/otp/verify
     * Body: { "phoneNumber": "+919876543210", "otp": "847291" }
     * Verifies the OTP for the given phone number.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@RequestBody OtpVerifyRequest request) {
        boolean valid = snsService.verifyOtp(request.getPhoneNumber(), request.getOtp());
        if (valid) {
            return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully.", null));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP."));
    }
}
