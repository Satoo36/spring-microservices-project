package com.microservices.order.client;

import com.microservices.order.client.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/*
 * FEIGN CLIENT — calls product-service
 * ======================================
 *
 * Two operations order-service needs from product-service:
 *   1. getProductById  → fetch price + stock before placing order
 *   2. reduceStock     → deduct stock after order is confirmed
 *
 * @FeignClient(name = "product-service")
 *   name matches spring.application.name in product-service's application.yml.
 *   path = "/api/products" applies as a base path to all methods in this interface.
 *
 * CLIENT-SIDE LOAD BALANCING:
 *   If product-service has 2 instances (e.g., ports 8083 and 8084),
 *   Spring Cloud LoadBalancer alternates between them — Round Robin.
 *   order-service never knows which instance it's talking to.
 *   No API gateway or external load balancer is involved in service-to-service calls.
 */
@FeignClient(name = "product-service", path = "/api/products")
public interface ProductServiceClient {

    @GetMapping("/{id}")
    ResponseEntity<ProductResponse> getProductById(@PathVariable("id") String id);

    /*
     * @RequestParam maps to ?quantity=N in the query string.
     * Must match exactly the @RequestParam name in ProductController.reduceStock().
     */
    @PutMapping("/{id}/reduce-stock")
    ResponseEntity<String> reduceStock(
            @PathVariable("id") String id,
            @RequestParam("quantity") int quantity);
}
