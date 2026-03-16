package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Service
public class SqsService {

    private final SqsClient sqsClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    public SqsService(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public String sendMessage(String message) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build();

        SendMessageResponse response = sqsClient.sendMessage(request);
        log.info("Message sent to SQS. MessageId: {}", response.messageId());
        return response.messageId();
    }
}
