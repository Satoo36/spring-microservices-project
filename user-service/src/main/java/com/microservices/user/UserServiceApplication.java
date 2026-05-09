package com.microservices.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * No special annotation needed for Eureka client in Spring Boot 3.x.
 * The spring-cloud-starter-netflix-eureka-client dependency on the classpath
 * is enough — Spring Cloud auto-configures the registration.
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
