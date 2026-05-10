package com.microservices.order.service;

import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.client.UserServiceClient;
import com.microservices.order.client.dto.ProductResponse;
import com.microservices.order.client.dto.UserResponse;
import com.microservices.order.model.CreateOrderRequest;
import com.microservices.order.model.Order;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * OrderService — orchestrates two Feign calls to create an order:
 *
 *   1. Validate user exists   → calls user-service   via UserServiceClient
 *   2. Fetch product + stock  → calls product-service via ProductServiceClient
 *   3. Reduce stock           → calls product-service via ProductServiceClient
 *   4. Persist order          → stores in ConcurrentHashMap (DB in Phase 8)
 *
 * CALL FLOW:
 *   client → gateway → order-service → user-service    (Feign, sync)
 *                                    → product-service  (Feign, sync, twice)
 *
 * This is SYNCHRONOUS ORCHESTRATION — order-service blocks on every Feign call.
 * Phase 4 adds Circuit Breaker so a down product-service doesn't crash order-service.
 * Phase 5 replaces the stock-reduction call with an async Kafka event.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final UserServiceClient    userServiceClient;
    private final ProductServiceClient productServiceClient;

    // In-memory store — replaced by JPA repository in Phase 8
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public OrderService(UserServiceClient userServiceClient,
                        ProductServiceClient productServiceClient) {
        this.userServiceClient    = userServiceClient;
        this.productServiceClient = productServiceClient;
    }

    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order: userId={}, productId={}, quantity={}",
                request.getUserId(), request.getProductId(), request.getQuantity());

        // ── Step 1: Validate user ──────────────────────────────────────────────
        // Feign call → GET http://user-service/api/users/{id}
        // FeignException.NotFound is thrown if user-service returns 404
        UserResponse user;
        try {
            ResponseEntity<UserResponse> userResponse =
                    userServiceClient.getUserById(request.getUserId());
            user = userResponse.getBody();
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("User not found: " + request.getUserId());
        } catch (FeignException e) {
            throw new RuntimeException("Failed to reach user-service: " + e.getMessage(), e);
        }
        log.info("User validated: {}", user.getName());

        // ── Step 2: Fetch product details (price + stock check) ────────────────
        // Feign call → GET http://product-service/api/products/{id}
        ProductResponse product;
        try {
            ResponseEntity<ProductResponse> productResponse =
                    productServiceClient.getProductById(request.getProductId());
            product = productResponse.getBody();
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Product not found: " + request.getProductId());
        } catch (FeignException e) {
            throw new RuntimeException("Failed to reach product-service: " + e.getMessage(), e);
        }

        if (product.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    "Insufficient stock. Requested: " + request.getQuantity() +
                    ", Available: " + product.getStock());
        }
        log.info("Product validated: {} | price={} | stock={}",
                product.getName(), product.getPrice(), product.getStock());

        // ── Step 3: Reduce stock ───────────────────────────────────────────────
        // Feign call → PUT http://product-service/api/products/{id}/reduce-stock?quantity=N
        try {
            productServiceClient.reduceStock(request.getProductId(), request.getQuantity());
        } catch (FeignException e) {
            throw new RuntimeException("Failed to reduce stock: " + e.getMessage(), e);
        }

        // ── Step 4: Persist order ──────────────────────────────────────────────
        double totalPrice = product.getPrice() * request.getQuantity();
        Order order = new Order(
                UUID.randomUUID().toString(),
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                totalPrice,
                "CONFIRMED"
        );
        orders.put(order.getId(), order);
        log.info("Order created: id={}, total={}", order.getId(), order.getTotalPrice());
        return order;
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
