package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.OrderRequest;
import org.example.model.Order;
import org.example.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Place a new order.
     * Creates order with PENDING status and pushes it to SQS.
     *
     * POST /order/place
     * Body: { "userId": "u1", "product": "iPhone 15", "quantity": 2 }
     */
    @PostMapping("/place")
    public ResponseEntity<ApiResponse<Order>> placeOrder(@RequestBody OrderRequest request) {
        if (request.getUserId() == null || request.getProduct() == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("userId, product, and quantity > 0 are required"));
        }

        Order order = orderService.placeOrder(request);
        return ResponseEntity.ok(ApiResponse.ok("Order placed successfully", order));
    }

    /**
     * Get order status by orderId.
     *
     * GET /order/{orderId}/status
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<Order>> getOrderStatus(@PathVariable String orderId) {
        return orderService.getOrder(orderId)
                .map(order -> ResponseEntity.ok(ApiResponse.ok("Order found", order)))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Order not found: " + orderId)));
    }

    /**
     * List all orders (all statuses).
     *
     * GET /order/all
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Collection<Order>>> getAllOrders() {
        Collection<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.ok("Orders retrieved", orders, orders.size()));
    }
}
