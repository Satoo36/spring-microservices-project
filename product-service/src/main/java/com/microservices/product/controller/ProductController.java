package com.microservices.product.controller;

import com.microservices.product.model.Product;
import com.microservices.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * ProductController — exposes two endpoints called by order-service via Feign:
 *
 * GET  /api/products/{id}                    → fetch product details (check price + stock)
 * PUT  /api/products/{id}/reduce-stock       → decrement stock after order is placed
 *
 * Why PUT for reduce-stock?
 * PUT is idempotent by HTTP spec, but stock reduction is actually NOT idempotent
 * (calling it twice reduces stock twice). In production you'd use POST + idempotency key.
 * Phase 10 covers the Idempotency Key pattern.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /*
     * Called by order-service after user + stock are validated.
     * @RequestParam quantity — how many units to deduct.
     * Returns 400 Bad Request with an error message if stock is insufficient.
     */
    @PutMapping("/{id}/reduce-stock")
    public ResponseEntity<String> reduceStock(
            @PathVariable String id,
            @RequestParam int quantity) {
        try {
            productService.reduceStock(id, quantity);
            return ResponseEntity.ok("Stock reduced successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
