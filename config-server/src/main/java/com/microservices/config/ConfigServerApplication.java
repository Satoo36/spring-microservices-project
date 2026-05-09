package com.microservices.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * @EnableConfigServer — activates the Config Server.
 * Once active, this app exposes endpoints like:
 *   GET /{application}/{profile}        → returns config for that service+profile
 *   GET /{application}/{profile}/{label} → specific git branch/label
 *
 * Example: GET /user-service/dev  → returns user-service-dev.yml content
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
