package org.example.model;

import java.time.Instant;

public class Order {

    private String orderId;
    private String userId;
    private String product;
    private int quantity;
    private OrderStatus status;
    private String createdAt;
    private String updatedAt;

    public Order(String orderId, String userId, String product, int quantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.product = product;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now().toString();
        this.updatedAt = this.createdAt;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now().toString();
    }
}
