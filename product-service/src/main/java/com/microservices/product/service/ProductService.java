package com.microservices.product.service;

import com.microservices.product.model.Product;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/*
 * ProductService — business logic for the product catalog.
 *
 * ConcurrentHashMap: thread-safe map used here because multiple Feign calls
 * from order-service can arrive concurrently.
 * Phase 8 will replace this with a JPA repository + PostgreSQL.
 */
@Service
public class ProductService {

    // Pre-seeded in-memory store — simulates a database
    private final Map<String, Product> products = new ConcurrentHashMap<>(Map.of(
            "p1", new Product("p1", "Laptop",     999.99, 50),
            "p2", new Product("p2", "Headphones", 149.99, 200),
            "p3", new Product("p3", "Mouse",       29.99, 5)   // low stock for testing
    ));

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(products.get(id));
    }

    /*
     * Reduces stock by the requested quantity.
     * Synchronized to prevent race conditions when two orders arrive simultaneously.
     * (Phase 8 will use @Version / optimistic locking in the DB instead.)
     *
     * Throws IllegalArgumentException if stock is insufficient —
     * order-service will catch this via Feign and fail the order.
     */
    public synchronized void reduceStock(String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        if (product.getStock() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient stock for " + product.getName() +
                    ". Requested: " + quantity + ", Available: " + product.getStock());
        }
        product.setStock(product.getStock() - quantity);
    }
}
