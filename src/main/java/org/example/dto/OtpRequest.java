package org.example.dto;

import lombok.Data;

@Data
public class OtpRequest {
    private String phoneNumber; // E.164 format, e.g. +919876543210
}
