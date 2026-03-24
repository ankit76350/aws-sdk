package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.SubscribeRequest;
import org.example.service.SnsTopicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sns/topic")
@RequiredArgsConstructor
public class SnsTopicController {

    private final SnsTopicService snsTopicService;

    @Value("${aws.sns.topic-arn}")
    private String defaultTopicArn;

    /**
     * POST /sns/topic/subscribe
     * Subscribe an email or phone number to the default topic.
     *
     * Body (email):  { "endpoint": "user@example.com", "protocol": "email" }
     * Body (SMS):    { "endpoint": "+919876543210",    "protocol": "sms"   }
     *
     * Optionally override the topic:
     *               { "endpoint": "...", "protocol": "...", "topicArn": "arn:aws:sns:..." }
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<String>> subscribe(@RequestBody SubscribeRequest request) {
        String topicArn = (request.getTopicArn() != null) ? request.getTopicArn() : defaultTopicArn;
        String subscriptionArn = snsTopicService.subscribe(topicArn, request.getProtocol(), request.getEndpoint());
        return ResponseEntity.ok(ApiResponse.ok("Subscribed successfully. Check endpoint for confirmation.", subscriptionArn));
    }

    /**
     * DELETE /sns/topic/unsubscribe
     * Body: { "subscriptionArn": "arn:aws:sns:eu-central-1:864456252731:Hello:xxxx" }
     */
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String subscriptionArn) {
        snsTopicService.unsubscribe(subscriptionArn);
        return ResponseEntity.ok(ApiResponse.ok("Unsubscribed successfully.", null));
    }
}
