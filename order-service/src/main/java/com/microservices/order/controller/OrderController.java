package com.microservices.order.controller;

import com.microservices.order.model.CreateOrderRequest;
import com.microservices.order.model.Order;
import com.microservices.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /*
     * POST /api/orders
     * Body: { "userId": "u1", "productId": "p1", "quantity": 2 }
     *
     * FULL CALL CHAIN:
     *   client → gateway(8080) → order-service(8082)
     *                          → user-service(8081)    [Feign: validate user]
     *                          → product-service(8083) [Feign: check + reduce stock]
     *
     * Returns 201 Created on success.
     * Returns 400 Bad Request if user/product not found or insufficient stock.
     * Returns 503 Service Unavailable if a downstream service is unreachable.
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            Order order = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        }
    }

    /*
     * GET /api/orders/{id}
     * Returns the order if found, 404 otherwise.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable String id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
