package com.microservices.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * @EnableEurekaServer — this single annotation activates the Eureka registry.
 * Without it, this is just a regular Spring Boot web app.
 * With it, Spring Cloud auto-configures the registry endpoints, the dashboard,
 * and all the heartbeat/registration handling.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
