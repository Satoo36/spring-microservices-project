package com.microservices.order.service;

import com.microservices.order.client.dto.ProductResponse;
import com.microservices.order.client.dto.UserResponse;
import com.microservices.order.model.CreateOrderRequest;
import com.microservices.order.model.Order;
import com.microservices.order.resilience.ProductServiceGateway;
import com.microservices.order.resilience.UserServiceGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * OrderService — Phase 4 update.
 *
 * Now uses ProductServiceGateway and UserServiceGateway instead of raw Feign clients.
 * OrderService knows nothing about resilience — it just calls the gateways.
 * Resilience is handled transparently by @CircuitBreaker / @Retry AOP proxies.
 *
 * CALL CHAIN (Phase 4):
 *   client → gateway → order-service → UserServiceGateway    (@Retry) → user-service
 *                                    → ProductServiceGateway (@CB + @Retry + @Bulkhead) → product-service
 *
 * If product-service is DOWN:
 *   First N calls  → @Retry retries N times, then @CircuitBreaker records failures
 *   After threshold → Circuit OPENS → all calls immediately go to fallback (ServiceUnavailableException)
 *   After wait time → Circuit HALF-OPENS → test calls allowed through
 *   If test calls succeed → Circuit CLOSES → normal operation resumes
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final UserServiceGateway    userServiceGateway;
    private final ProductServiceGateway productServiceGateway;

    // In-memory store — replaced by JPA repository in Phase 8
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public OrderService(UserServiceGateway userServiceGateway,
                        ProductServiceGateway productServiceGateway) {
        this.userServiceGateway    = userServiceGateway;
        this.productServiceGateway = productServiceGateway;
    }

    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order: userId={}, productId={}, quantity={}",
                request.getUserId(), request.getProductId(), request.getQuantity());

        // Step 1: Validate user — @Retry handles transient failures automatically
        UserResponse user = userServiceGateway.getUser(request.getUserId());
        log.info("User validated: {}", user.getName());

        // Step 2: Fetch product — @CircuitBreaker + @Retry handles failures
        ProductResponse product = productServiceGateway.getProduct(request.getProductId());

        if (product.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    "Insufficient stock. Requested: " + request.getQuantity() +
                    ", Available: " + product.getStock());
        }
        log.info("Product validated: {} | price={} | stock={}",
                product.getName(), product.getPrice(), product.getStock());

        // Step 3: Reduce stock — @CircuitBreaker + @Bulkhead handles failures
        productServiceGateway.reduceStock(request.getProductId(), request.getQuantity());

        // Step 4: Save order
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
