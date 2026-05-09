package com.microservices.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @RefreshScope — this is the key annotation for live config refresh.
 *
 * Normally, @Value fields are injected once at startup and never change.
 * @RefreshScope tells Spring: "when /actuator/refresh is called,
 * recreate this bean and re-inject all @Value fields with fresh values."
 *
 * Without @RefreshScope, calling /actuator/refresh would do nothing visible.
 */
@RestController
@RequestMapping("/api/users")
@RefreshScope
public class GreetingController {

    /**
     * @Value reads from the config served by Config Server.
     * The value comes from config-repo/user-service.yml → app.greeting
     */
    @Value("${app.greeting:Hello! (no config server)}")
    private String greeting;

    @Value("${app.feature-flag:false}")
    private boolean featureFlag;

    @GetMapping("/greeting")
    public String greeting() {
        return greeting + " | feature-flag=" + featureFlag;
    }

    @GetMapping("/health-check")
    public String healthCheck() {
        return "user-service is UP";
    }
}
