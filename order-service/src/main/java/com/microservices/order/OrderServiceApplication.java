package com.microservices.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/*
 * ================================================================
 * PHASE 3 — INTER-SERVICE COMMUNICATION (Feign + LoadBalancer)
 * PHASE 4 — RESILIENCE (Circuit Breaker, Retry, Bulkhead)
 * ================================================================
 *
 * WHAT IS A FEIGN CLIENT?
 * -----------------------
 * OpenFeign is a declarative HTTP client. You define an interface with
 * Spring MVC annotations (@GetMapping, @PostMapping, etc.) and annotate
 * it with @FeignClient. Spring generates a working HTTP client at startup
 * — no RestTemplate or WebClient boilerplate needed.
 *
 * Example:
 *   @FeignClient(name = "user-service")      ← Eureka service name
 *   public interface UserServiceClient {
 *       @GetMapping("/api/users/{id}")
 *       UserResponse getUserById(@PathVariable("id") String id);
 *   }
 *   // order-service calls: userServiceClient.getUserById("u1")
 *   // Feign translates to: GET http://<user-service-host>:<port>/api/users/u1
 *
 * HOW LOAD BALANCING WORKS (Feign + Eureka):
 * -------------------------------------------
 * 1. @FeignClient(name="user-service") → "lb://user-service" URI internally
 * 2. Spring Cloud LoadBalancer asks Eureka: "give me instances of user-service"
 * 3. LoadBalancer picks one instance (Round Robin by default)
 * 4. Feign makes the HTTP call to that instance's host:port
 * 5. If that instance is down, LoadBalancer retries with another instance
 *
 * The `name` in @FeignClient MUST EXACTLY match `spring.application.name`
 * of the target service (case-insensitive for Eureka, but case-sensitive in YAML).
 *
 * HOW JWT PROPAGATION WORKS (RequestInterceptor):
 * ------------------------------------------------
 * Problem: order-service receives a JWT from the client (via gateway).
 *          When order-service calls product-service via Feign, Feign creates
 *          a new HTTP request — the JWT is NOT automatically carried over.
 *
 * Solution: Implement RequestInterceptor.
 *   1. Order-service receives request with Authorization: Bearer <token>
 *   2. FeignClientInterceptor reads the token from the current Servlet thread
 *      using RequestContextHolder (ThreadLocal storage for the current request)
 *   3. Interceptor adds it to every outgoing Feign request template
 *   4. product-service and user-service receive the token and can validate it
 *
 * DRAWBACKS OF SYNCHRONOUS COMMUNICATION:
 * ----------------------------------------
 * 1. TIGHT COUPLING: if product-service is down, order-service fails completely
 * 2. CASCADING FAILURES: one slow service blocks the entire call chain
 * 3. LATENCY ADDITION: total latency = sum of all downstream call latencies
 * 4. THREAD BLOCKING: Feign blocks the thread while waiting for a response
 *    (servlet container has limited thread pool — high latency = thread starvation)
 *
 * Solutions introduced later:
 *   Phase 4 → Circuit Breaker (stop calling a failing service)
 *   Phase 5 → Kafka (async messaging — services don't block each other)
 *
 * ================================================================
 * INTERVIEW Q&A
 * ================================================================
 *
 * Q1: What is a Feign client?
 * A:  Declarative HTTP client from OpenFeign (integrated into Spring Cloud).
 *     You define a Java interface with Spring MVC annotations + @FeignClient.
 *     Spring generates the implementation at startup. Replaces manual RestTemplate
 *     boilerplate. Also integrates with Spring Cloud LoadBalancer and Eureka
 *     automatically — just use the service name, not a hardcoded URL.
 *
 * Q2: How does load balancing work with Feign + Eureka?
 * A:  Feign resolves the service name via Eureka to get a list of live instances.
 *     Spring Cloud LoadBalancer (successor to Netflix Ribbon) picks one instance
 *     using Round Robin by default. If an instance is unhealthy (removed from Eureka),
 *     it won't be selected. The balancing is CLIENT-SIDE — order-service decides which
 *     instance to call, not a centralized load balancer.
 *
 * Q3: How do you propagate security context (JWT) across service calls?
 * A:  Implement Feign's RequestInterceptor interface. In apply(), read the JWT from
 *     the current thread's request (via RequestContextHolder / ServletRequestAttributes),
 *     then add it to the outgoing Feign template: template.header("Authorization", token).
 *     This interceptor runs on EVERY Feign call automatically, so no service call
 *     will accidentally drop the auth header.
 *
 * Q4: What are the drawbacks of synchronous inter-service communication?
 * A:  (1) Temporal coupling — both services must be up simultaneously.
 *     (2) Cascading failures — one slow service stalls the entire request chain.
 *     (3) Latency multiplication — total time = sum of all downstream call times.
 *     (4) Thread exhaustion — blocked threads reduce throughput under load.
 *     Circuit Breaker (Phase 4) and async messaging via Kafka (Phase 5) mitigate these.
 * ================================================================
 *
 * ================================================================
 * PHASE 4 — RESILIENCE INTERVIEW Q&A
 * ================================================================
 *
 * Q1: What is the Circuit Breaker pattern? What are its states?
 * A:  Monitors calls to a downstream service. If the failure rate exceeds a
 *     threshold, the circuit "trips" and subsequent calls immediately go to a
 *     fallback — no actual call is made. This gives the failing service time to
 *     recover without being hammered with traffic.
 *
 *     Three states:
 *       CLOSED    → normal operation; all calls go through; failures are counted.
 *       OPEN      → circuit tripped; all calls short-circuit to fallback immediately.
 *                   Saves resources and lets the downstream service recover.
 *       HALF-OPEN → after a wait duration, a small number of test calls are allowed.
 *                   If they succeed → circuit closes (recovered).
 *                   If they fail   → circuit opens again (still broken).
 *
 * Q2: What is the difference between a Retry and a Circuit Breaker?
 * A:  Retry:          re-attempts the SAME call after a failure. Handles transient
 *                     (temporary) failures — network blips, short restart windows.
 *                     Risk: if service is fully down, retries waste resources and
 *                     add latency for every request.
 *     Circuit Breaker: stops calling the failing service entirely once the failure
 *                     rate crosses the threshold. Short-circuits immediately while
 *                     OPEN — saves threads, reduces load on the broken service.
 *
 *     Best practice: stack them — Retry (inner) inside CircuitBreaker (outer).
 *       CB sees retries as one logical attempt → fair failure rate calculation.
 *
 * Q3: What is the Bulkhead pattern?
 * A:  Isolates a resource (threads or concurrent calls) for each downstream dependency.
 *     If product-service is slow and consuming all available threads, the Bulkhead
 *     caps the number of concurrent calls to it. Other services (user-service, etc.)
 *     are unaffected — they have their own "compartment" of threads.
 *
 *     Analogy: a ship's bulkhead separates compartments. If one floods, others stay dry.
 *
 *     Two types in Resilience4j:
 *       SEMAPHORE   → limits concurrent calls (same thread); lightweight
 *       THREADPOOL  → uses a dedicated thread pool; full thread isolation
 *
 * Q4: How do you configure Resilience4j in Spring Boot?
 * A:  Add spring-cloud-starter-circuitbreaker-resilience4j + spring-boot-starter-aop.
 *     Annotate methods: @CircuitBreaker(name="x", fallbackMethod="y"), @Retry, @Bulkhead.
 *     Configure thresholds in application.yml under resilience4j.circuitbreaker.instances.x.
 *     Expose via actuator: management.health.circuitbreakers.enabled=true.
 *     Check health: GET /actuator/health → shows each CB state (CLOSED/OPEN/HALF_OPEN).
 *
 *     IMPORTANT: annotations use Spring AOP — the annotated method must be called
 *     through the Spring proxy (from a different bean), not via self-invocation.
 * ================================================================
 *
 * @EnableFeignClients — scans this package (and sub-packages) for @FeignClient
 * interfaces and registers them as Spring beans.
 * Without this annotation, no Feign client will be created.
 */
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
