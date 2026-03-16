package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.service.SqsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/sqs")
public class SqsController {

    private final SqsService sqsService;

    public SqsController(SqsService sqsService) {
        this.sqsService = sqsService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendMessage(@RequestBody Map<String, String> body) {
        System.out.println("🌈🌈 Received request to send message: " + body);
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message field is required"));
        }

        log.info("Sending message to SQS: {}", message);
        String messageId = sqsService.sendMessage(message);
        return ResponseEntity.ok(Map.of("messageId", messageId, "status", "sent"));
    }
}
