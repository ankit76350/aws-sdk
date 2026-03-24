package org.example.dto;

import lombok.Data;

@Data
public class OtpVerifyRequest {
    private String phoneNumber; // E.164 format, e.g. +919876543210
    private String otp;
}
