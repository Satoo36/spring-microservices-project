package com.microservices.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * ================================================================
 * PHASE 2 — API GATEWAY (Spring Cloud Gateway)
 * ================================================================
 *
 * WHAT IS AN API GATEWAY?
 * -----------------------
 * A single entry point for all client traffic. Instead of clients
 * knowing about individual service addresses, they only talk to the
 * gateway. The gateway then:
 *   1. Matches the request to a Route (via Predicates)
 *   2. Applies Filters (auth, logging, rate limit, header rewrite)
 *   3. Forwards to the downstream service (via lb:// URI)
 *   4. Returns the response back to the client
 *
 * WHY NOT ZUUL?
 * -------------
 * Zuul 1.x  → Servlet-based, BLOCKING I/O, one thread per request.
 *              Thread pool is the bottleneck at high concurrency.
 * SCG        → WebFlux-based, NON-BLOCKING I/O, Project Reactor.
 *              A small number of threads handle thousands of requests
 *              via event loop (same model as Node.js / Nginx).
 * Zuul 2.x was async but never officially adopted into Spring Cloud.
 *
 * HOW ROUTING WORKS (Route = id + uri + predicates[] + filters[]):
 * ----------------------------------------------------------------
 * Predicate  = "does this request match this route?"
 *              Examples: Path=/api/users/**, Method=GET, Header=X-Version=v2
 * Filter     = "mutate request/response before or after forwarding"
 *              Examples: AddRequestHeader, StripPrefix, RequestRateLimiter
 * URI        = where to forward:
 *              lb://user-service  → Eureka lookup + client-side load balancing
 *              http://localhost:8081 → direct (no discovery)
 *
 * lb://user-service step-by-step:
 *   1. Gateway calls Eureka: "give me instances of USER-SERVICE"
 *   2. Spring Cloud LoadBalancer picks one (Round Robin by default)
 *   3. Gateway replaces lb://user-service with http://<chosen-host>:<port>
 *   4. Request is forwarded
 *
 * FILTER CHAIN:
 * -------------
 * GlobalFilter   = applied automatically to ALL routes (e.g., logging)
 * GatewayFilter  = applied only to the route where it is declared
 *
 * Execution order (lower Ordered value runs first in pre, last in post):
 *   Pre-phase:   filter(order=1) → filter(order=2) → proxy call
 *   Post-phase:  filter(order=2) → filter(order=1) → response to client
 *
 * RATE LIMITING (Token Bucket via Redis):
 * ----------------------------------------
 * replenishRate  = tokens added per second (sustained throughput)
 * burstCapacity  = max tokens in bucket (peak allowed)
 * KeyResolver    = how to identify a "client" (by IP, user ID, API key)
 * When bucket is empty → HTTP 429 Too Many Requests returned immediately.
 *
 * ================================================================
 * INTERVIEW Q&A — Read this before your interview!
 * ================================================================
 *
 * Q1: What does an API Gateway do?
 * A:  Central entry point for all external clients. It handles:
 *     routing (to correct service), authentication/authorization,
 *     rate limiting, SSL termination, request/response transformation,
 *     CORS, and logging — removing these concerns from individual services.
 *
 * Q2: What is the difference between Zuul and Spring Cloud Gateway?
 * A:  Zuul 1.x = servlet/blocking (one thread per request, Tomcat).
 *     Spring Cloud Gateway = WebFlux/non-blocking (event loop, Netty).
 *     SCG handles higher concurrency with fewer threads. Zuul 2.x (async)
 *     was developed by Netflix but not formally adopted into Spring Cloud.
 *
 * Q3: How do you implement rate limiting in a gateway?
 * A:  Use RedisRateLimiter with the RequestRateLimiter GatewayFilter.
 *     Redis stores a token bucket per "key" (e.g., client IP).
 *     Configure replenishRate (tokens/sec) and burstCapacity (bucket size).
 *     When bucket is empty, gateway returns HTTP 429 without forwarding.
 *     KeyResolver determines how to partition limits (per IP / user / API key).
 *
 * Q4: What is a filter chain in the context of a gateway?
 * A:  An ordered sequence of GlobalFilters + route-specific GatewayFilters.
 *     Each filter has a pre-phase (before proxy) and post-phase (after response).
 *     Filters run in Ordered sequence — lower integer = higher priority.
 *     Enables cross-cutting concerns (auth, logging, tracing) without modifying
 *     individual service code.
 * ================================================================
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
