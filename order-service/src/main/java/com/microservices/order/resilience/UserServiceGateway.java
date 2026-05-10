package com.microservices.order.resilience;

import com.microservices.order.client.UserServiceClient;
import com.microservices.order.client.dto.UserResponse;
import feign.FeignException;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/*
 * UserServiceGateway — resilience wrapper around UserServiceClient
 * =================================================================
 *
 * Only @Retry here (no CircuitBreaker) to show the contrast with ProductServiceGateway.
 *
 * RETRY PATTERN:
 * --------------
 * Automatically re-attempts a failed operation up to N times before giving up.
 * Useful for: transient network failures, momentary service restarts, brief timeouts.
 * NOT useful for: business errors (404 user not found), auth failures (401/403).
 *
 * KEY CONFIG (in application.yml):
 *   max-attempts: 3            → try 3 times total (1 initial + 2 retries)
 *   wait-duration: 500ms       → wait 500ms between attempts
 *   retry-exceptions: [...]    → retry ONLY on these exception types
 *   ignore-exceptions: [...]   → NEVER retry on these (fail fast)
 *
 * RETRY vs CIRCUIT BREAKER:
 * --------------------------
 * Retry:          "Try again — maybe it was a blip."
 *                 Keeps hammering the failing service on every request.
 *                 Risk: if the service is fully down, retries cause extra load.
 *
 * CircuitBreaker: "Stop calling — the service is broken, give it time to recover."
 *                 Short-circuits all calls while circuit is OPEN.
 *                 Reduces load on the failing service → faster recovery.
 *
 * Best practice: use BOTH — Retry for transient glitches, CB for sustained failures.
 * Stack order: CB(outer) → Retry(inner) → actual call.
 */
@Service
public class UserServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(UserServiceGateway.class);

    private final UserServiceClient userServiceClient;

    public UserServiceGateway(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /*
     * @Retry(name = "user-service") maps to resilience4j.retry.instances.user-service in yml.
     *
     * fallbackMethod is called after ALL retry attempts fail.
     * IMPORTANT: ignore-exceptions in yml excludes FeignException.NotFound (404)
     * from being retried — retrying "user not found" would waste 3 attempts for no reason.
     */
    @Retry(name = "user-service", fallbackMethod = "getUserFallback")
    public UserResponse getUser(String userId) {
        log.debug("Calling user-service.getUser({})", userId);
        ResponseEntity<UserResponse> response = userServiceClient.getUserById(userId);

        // 404 is a business error, not a service failure — fail fast, no retry
        if (response.getStatusCode().value() == 404 || response.getBody() == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return response.getBody();
    }

    /*
     * Fallback: called only after max-attempts retries are ALL exhausted.
     * If the exception is a business error (IllegalArgumentException = user not found),
     * propagate it as-is — no point wrapping it in ServiceUnavailableException.
     */
    public UserResponse getUserFallback(String userId, Throwable t) {
        if (t instanceof IllegalArgumentException) {
            throw (IllegalArgumentException) t;
        }
        log.error("[FALLBACK] getUser failed after retries. userId={}, reason={}",
                userId, t.getMessage());
        throw new ServiceUnavailableException(
                "User service is temporarily unavailable. Please try again later.");
    }
}
