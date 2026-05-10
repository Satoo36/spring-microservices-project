package com.microservices.order.resilience;

import com.microservices.order.client.ProductServiceClient;
import com.microservices.order.client.dto.ProductResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/*
 * ProductServiceGateway — resilience wrapper around ProductServiceClient
 * =======================================================================
 *
 * WHY A SEPARATE CLASS?
 * ----------------------
 * @CircuitBreaker and @Retry work via Spring AOP proxies.
 * AOP only intercepts calls that go THROUGH the Spring proxy (i.e., calls
 * from OUTSIDE the bean). If OrderService called these methods on itself
 * (self-invocation), the AOP proxy would be bypassed and resilience
 * annotations would be silently ignored — a common bug in Spring apps.
 *
 * Solution: extract all Feign calls into a separate @Service bean.
 * OrderService → (proxy) → ProductServiceGateway → ProductServiceClient (Feign)
 *                ↑ AOP intercepts here
 *
 * STACKING ORDER (outermost to innermost):
 * ----------------------------------------
 * Resilience4j's default Spring AOP aspect order:
 *   @Bulkhead      (aspect order = highest priority = outermost wrapper)
 *     → @CircuitBreaker
 *       → @Retry
 *         → actual Feign call
 *
 * Execution for a SINGLE request:
 *   1. Bulkhead checks: are there fewer than maxConcurrentCalls active? → allow or reject
 *   2. CircuitBreaker checks: is circuit CLOSED or HALF-OPEN? → allow or short-circuit to fallback
 *   3. Retry: attempts the call; on failure, waits and retries up to maxAttempts
 *   4. Actual Feign call executes
 *
 * WHY THIS ORDER?
 *   Retry inside CircuitBreaker = the CB sees ONE logical attempt (all retries count as one).
 *   If retries fail, CB records that as one failure → fair threshold counting.
 *   If CB was outside Retry, retries could happen even when circuit is OPEN (wasteful).
 *
 * ================================================================
 * CIRCUIT BREAKER — States
 * ================================================================
 *
 *  CLOSED  ──(failure rate > threshold)──▶  OPEN  ──(wait duration elapsed)──▶  HALF-OPEN
 *    ▲                                                                                │
 *    └──────────────(test calls succeed)────────────────────────────────────────────┘
 *
 * CLOSED:    Normal operation. Every call goes through. Failure rate is tracked.
 * OPEN:      Circuit trips. All calls immediately go to fallback (no actual call made).
 *            Protects the downstream service from being hammered while it recovers.
 * HALF-OPEN: After wait-duration, a small number of test calls are allowed through.
 *            If they succeed → circuit closes (service recovered).
 *            If they fail   → circuit opens again (not recovered yet).
 *
 * ================================================================
 * BULKHEAD — Concurrency Limiter
 * ================================================================
 * Limits concurrent calls to a downstream service.
 * Prevents a slow/overloaded product-service from consuming ALL threads in order-service.
 *
 * Two types:
 *   SEMAPHORE   (default): same thread, just counts concurrent calls
 *   THREADPOOL:            dedicated thread pool, actual thread isolation
 *
 * Analogy: a ship's bulkhead separates compartments. If one floods (is slow),
 * others stay dry (unaffected). Here: if product-service is slow, the bulkhead
 * prevents it from blocking all of order-service's threads.
 */
@Service
public class ProductServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceGateway.class);

    private final ProductServiceClient productServiceClient;

    public ProductServiceGateway(ProductServiceClient productServiceClient) {
        this.productServiceClient = productServiceClient;
    }

    // ── getProduct ─────────────────────────────────────────────────────────────
    /*
     * Execution stack: CircuitBreaker wraps Retry wraps actual Feign call.
     *
     * "product-service" name maps to resilience4j.circuitbreaker.instances.product-service
     * and resilience4j.retry.instances.product-service in application.yml.
     *
     * fallbackMethod: called when circuit is OPEN, or all retries exhausted.
     * Must have identical parameters as this method + Throwable at the end.
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "getProductFallback")
    @Retry(name = "product-service")
    public ProductResponse getProduct(String productId) {
        log.debug("Calling product-service.getProduct({})", productId);
        ResponseEntity<ProductResponse> response = productServiceClient.getProductById(productId);
        if (response.getStatusCode().value() == 404 || response.getBody() == null) {
            // 404 is not a service failure — don't let CB count it as a failure
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        return response.getBody();
    }

    /*
     * Fallback for getProduct — called when:
     *   (a) circuit is OPEN (product-service has been failing), OR
     *   (b) @Retry exhausted all attempts
     *
     * For read operations, we COULD return a default/cached response (graceful degradation).
     * Here we throw instead — can't place an order with unknown product details.
     * In a real system, you'd cache the last-known product and return that.
     */
    public ProductResponse getProductFallback(String productId, Throwable t) {
        // Don't swallow business exceptions — propagate as-is
        if (t instanceof IllegalArgumentException) {
            throw (IllegalArgumentException) t;
        }
        log.error("[FALLBACK] getProduct failed for productId={}. Circuit may be OPEN. reason={}",
                productId, t.getMessage());
        throw new ServiceUnavailableException(
                "Product service is temporarily unavailable. Please try again later.");
    }

    // ── reduceStock ────────────────────────────────────────────────────────────
    /*
     * @Bulkhead(SEMAPHORE): max 10 concurrent calls to this method.
     * If 10 calls are already in-flight and an 11th arrives, it is
     * immediately rejected (throws BulkheadFullException → goes to fallback).
     *
     * @CircuitBreaker wraps @Bulkhead so a fully-loaded bulkhead doesn't trip the circuit.
     */
    @CircuitBreaker(name = "product-service", fallbackMethod = "reduceStockFallback")
    @Bulkhead(name = "product-service", type = Bulkhead.Type.SEMAPHORE,
              fallbackMethod = "reduceStockFallback")
    public void reduceStock(String productId, int quantity) {
        log.debug("Calling product-service.reduceStock({}, {})", productId, quantity);
        ResponseEntity<String> response = productServiceClient.reduceStock(productId, quantity);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalArgumentException("Stock reduction failed: " + response.getBody());
        }
    }

    // Fallback: quantity is needed in the signature to match the original method
    public void reduceStockFallback(String productId, int quantity, Throwable t) {
        if (t instanceof IllegalArgumentException) {
            throw (IllegalArgumentException) t;
        }
        log.error("[FALLBACK] reduceStock failed for productId={}, qty={}. reason={}",
                productId, quantity, t.getMessage());
        // Can NOT silently skip stock reduction — this would cause phantom orders
        throw new ServiceUnavailableException(
                "Cannot place order — product service is temporarily unavailable.");
    }
}
