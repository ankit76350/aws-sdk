package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.OrderRequest;
import org.example.model.Order;
import org.example.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderService {

    // In-memory store — acts like a lightweight DB for this demo
    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    private final SqsService sqsService;
    private final ObjectMapper objectMapper;

    public OrderService(SqsService sqsService, ObjectMapper objectMapper) {
        this.sqsService = sqsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Place a new order:
     * 1. Create Order object with PENDING status
     * 2. Store it in memory
     * 3. Serialize to JSON and push to SQS
     */
    public Order placeOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, request.getUserId(), request.getProduct(), request.getQuantity());

        orderStore.put(orderId, order);
        log.info("Order created: {} for user: {}", orderId, request.getUserId());

        try {
            String messageBody = objectMapper.writeValueAsString(Map.of(
                    "orderId", orderId,
                    "userId", request.getUserId(),
                    "product", request.getProduct(),
                    "quantity", request.getQuantity()
            ));
            sqsService.sendMessage(messageBody);
            log.info("Order {} pushed to SQS queue", orderId);
        } catch (Exception e) {
            log.error("Failed to push order {} to SQS: {}", orderId, e.getMessage());
            order.setStatus(OrderStatus.FAILED);
        }

        return order;
    }

    /**
     * Called by OrderWorker after pulling a message from SQS.
     * Simulates order processing (payment, inventory, email, etc.)
     */
    public void processOrder(String messageBody, String receiptHandle) {
        try {
            Map<?, ?> data = objectMapper.readValue(messageBody, Map.class);
            String orderId = (String) data.get("orderId");

            Order order = orderStore.get(orderId);
            if (order == null) {
                log.warn("Received SQS message for unknown orderId: {}", orderId);
                sqsService.deleteMessage(receiptHandle);
                return;
            }

            order.setStatus(OrderStatus.PROCESSING);
            log.info("Processing order {} | product: {} | qty: {}", orderId, data.get("product"), data.get("quantity"));

            // Simulate processing time (payment check, stock update, email, etc.)
            Thread.sleep(500);

            order.setStatus(OrderStatus.COMPLETED);
            log.info("Order {} completed successfully", orderId);

            // Delete from SQS only after successful processing
            log.info("Deleting order {} message from SQS", orderId );
            log.info("Receipt handle: {}", receiptHandle );
            sqsService.deleteMessage(receiptHandle);
            log.info("Order {} message deleted from SQS", orderId);

        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage());
            // Do NOT delete — message will reappear after visibility timeout and be retried
        }
    }

    public Optional<Order> getOrder(String orderId) {
        return Optional.ofNullable(orderStore.get(orderId));
    }

    public Collection<Order> getAllOrders() {
        return orderStore.values();
    }
}
