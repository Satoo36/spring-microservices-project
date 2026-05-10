package com.microservices.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * product-service — Phase 3
 * Exposes product catalog endpoints called by order-service via Feign.
 *
 * No special Spring Cloud annotations needed here:
 * - Eureka client is auto-configured by spring-cloud-starter-netflix-eureka-client
 *   when it is on the classpath.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}