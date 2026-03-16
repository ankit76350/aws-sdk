package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.service.SqsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @GetMapping("/receive")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> receiveMessages(
            @RequestParam(defaultValue = "10") int maxMessages) {

        List<Message> messages = sqsService.receiveMessages(maxMessages);

        List<Map<String, String>> result = messages.stream()
                .map(msg -> Map.of(
                        "messageId", msg.messageId(),
                        "receiptHandle", msg.receiptHandle(),
                        "body", msg.body(),
                        "size", msg.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes"
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Messages retrieved", result, result.size()));
    }

    /**
     * Looks up a single SQS message by its messageId.
     *
     * KNOWN LIMITATION — returns 404 even when the message exists:
     *
     * SQS does not support querying by messageId. This endpoint fetches
     * up to 10 currently visible messages and scans for a match.
     *
     * A 404 response means one of the following:
     *   - The message is IN-FLIGHT: it was already received and is hidden
     *     during the visibility timeout (default 30s). Wait and retry.
     *   - The message is not in the current batch of 10 returned by SQS.
     *   - The message was already deleted after processing.
     *
     * For reliable message lookup, store messages in a database on receipt.
     */
    @GetMapping("/receive/{messageId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> receiveMessageById(
            @PathVariable String messageId) {

        Optional<Message> found = sqsService.receiveMessageById(messageId);

        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("Message not found: " + messageId));
        }

        Message msg = found.get();
        Map<String, String> result = Map.of(
                "messageId", msg.messageId(),
                "receiptHandle", msg.receiptHandle(),
                "body", msg.body()
        );

        return ResponseEntity.ok(ApiResponse.ok("Message found", result));
    }
}
