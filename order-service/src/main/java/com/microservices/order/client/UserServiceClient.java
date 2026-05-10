package com.microservices.order.client;

import com.microservices.order.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
 * FEIGN CLIENT — calls user-service
 * ===================================
 *
 * @FeignClient(name = "user-service")
 *   `name` MUST match the spring.application.name of user-service.
 *   Spring Cloud LoadBalancer uses this name to look up instances in Eureka.
 *   Feign internally builds the URI: lb://user-service/api/users/{id}
 *   which resolves to http://<chosen-instance-host>:<port>/api/users/{id}
 *
 * This interface has NO implementation class — Spring generates a proxy
 * at startup via @EnableFeignClients on OrderServiceApplication.
 *
 * The method signature MIRRORS the endpoint in UserController exactly:
 *   @GetMapping("/{id}")  → matches GET /api/users/{id}
 *   ResponseEntity<UserResponse> → maps the response status + body
 *
 * If user-service returns 404, Feign throws FeignException.NotFound.
 * OrderService catches this and returns a meaningful error to the client.
 */
@FeignClient(name = "user-service", path = "/api/users")
public interface UserServiceClient {

    @GetMapping("/{id}")
    ResponseEntity<UserResponse> getUserById(@PathVariable("id") String id);
}
