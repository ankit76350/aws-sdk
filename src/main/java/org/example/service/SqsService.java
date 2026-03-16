package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.List;
import java.util.Optional;

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

    public List<Message> receiveMessages(int maxMessages) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages = sqsClient.receiveMessage(request).messages();
        log.info("Received {} message(s) from SQS", messages.size());
        return messages;
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
     * - The message is IN-FLIGHT: it was already received and is hidden
     * during the visibility timeout (default 30s). Wait and retry.
     * - The message is not in the current batch of 10 returned by SQS.
     * - The message was already deleted after processing.
     *
     * For reliable message lookup, store messages in a database on receipt.
     */

    public void deleteMessage(String receiptHandle) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        sqsClient.deleteMessage(request);
        log.info("Message deleted from SQS");
    }

    public Optional<Message> receiveMessageById(String messageId) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build();

        return sqsClient.receiveMessage(request).messages().stream()
                .filter(msg -> msg.messageId().equals(messageId))
                .findFirst();
    }
}
