package com.microservices.order.resilience;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 * Thrown by resilience fallback methods when a downstream service is unavailable.
 * @ResponseStatus(503) causes Spring MVC to return HTTP 503 automatically
 * when this exception reaches the controller (no explicit try-catch needed there).
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
