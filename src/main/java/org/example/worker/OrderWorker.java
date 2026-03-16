package org.example.worker;

import lombok.extern.slf4j.Slf4j;
import org.example.service.OrderService;
import org.example.service.SqsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

/**
 * Background worker that continuously polls SQS for new orders and processes them.
 *
 * Flow:
 *   1. Every 5 seconds, poll SQS for up to 5 messages
 *   2. For each message, hand off to OrderService.processOrder()
 *   3. OrderService updates order status: PENDING → PROCESSING → COMPLETED
 *   4. On success, message is deleted from SQS
 *   5. On failure, message is NOT deleted — SQS will make it visible again after timeout
 */
@Slf4j
@Component
public class OrderWorker {

    private final SqsService sqsService;
    private final OrderService orderService;

    public OrderWorker(SqsService sqsService, OrderService orderService) {
        this.sqsService = sqsService;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollAndProcess() {
        List<Message> messages = sqsService.receiveMessages(5);

        if (messages.isEmpty()) {
            log.debug("No messages in queue");
            return;
        }

        log.info("Worker picked up {} order(s) from SQS", messages.size());

        for (Message message : messages) {
            orderService.processOrder(message.body(), message.receiptHandle());
        }
    }
}
