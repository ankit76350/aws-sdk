package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.ApiResponse;
import org.example.service.SesService;
import org.example.service.SqsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final SqsService sqsService;
    private final SesService sesService;

    public NotificationController(SqsService sqsService, SesService sesService) {
        this.sqsService = sqsService;
        this.sesService = sesService;
    }

    /**
     * POST /notification/send
     * Sends a message body to SQS.
     *
     * Request body:
     * {
     *   "message": "Hello, this is a notification!"
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendToSqs(
            @RequestBody Map<String, String> body) {

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("'message' field is required"));
        }

        log.info("Sending notification message to SQS: {}", message);
        String messageId = sqsService.sendMessage(message);

        return ResponseEntity.ok(
                ApiResponse.ok("Message sent to SQS", Map.of("messageId", messageId)));
    }

    /**
     * POST /notification/poll-and-email
     * Polls SQS for up to maxMessages, sends each as an email via SES,
     * then deletes the message from the queue.
     *
     * Query param: maxMessages (default 5)
     */
    @PostMapping("/poll-and-email")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> pollAndEmail(
            @RequestParam(defaultValue = "5") int maxMessages) {

        log.info("Polling SQS for up to {} messages to email", maxMessages);
        List<Message> messages = sqsService.receiveMessages(maxMessages);

        if (messages.isEmpty()) {
            return ResponseEntity.ok(
                    ApiResponse.ok("No messages found in queue", List.of(), 0));
        }

        List<Map<String, String>> results = new ArrayList<>();

        for (Message msg : messages) {
            String subject = "SQS Notification - " + msg.messageId();
            String emailBody = "You have a new message from SQS:\n\n" + msg.body();

            try {
                String sesMessageId = sesService.sendEmail(subject, emailBody);
                sqsService.deleteMessage(msg.receiptHandle());

                results.add(Map.of(
                        "sqsMessageId", msg.messageId(),
                        "sesMessageId", sesMessageId,
                        "status", "emailed_and_deleted"
                ));
                log.info("Emailed and deleted SQS message: {}", msg.messageId());

            } catch (Exception e) {
                log.error("Failed to email SQS message {}: {}", msg.messageId(), e.getMessage());
                results.add(Map.of(
                        "sqsMessageId", msg.messageId(),
                        "status", "failed",
                        "error", e.getMessage()
                ));
            }
        }

        return ResponseEntity.ok(
                ApiResponse.ok("Poll and email complete", results, results.size()));
    }
}
