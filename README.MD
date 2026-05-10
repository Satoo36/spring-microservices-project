# Spring Microservices Project

A hands-on project to learn every core microservices concept asked in interviews.
Built with Spring Boot 3.x, Spring Cloud, Java 21, and Docker.

---

## Project Structure

```
microservices-project/
├── eureka-server/          port 8761   — Service Registry & Discovery
├── api-gateway/            port 8080   — Single entry point, routing, filters
├── config-server/          port 8888   — Centralized configuration
├── user-service/           port 8081   — User management domain
├── order-service/          port 8082   — Order management domain
├── product-service/        port 8083   — Product catalog domain
├── notification-service/   port 8084   — Async event-driven notifications
└── docker-compose.yml                  — Full stack local setup
```

---

## Tech Stack

| Layer              | Technology                              |
|--------------------|-----------------------------------------|
| Framework          | Spring Boot 3.x                         |
| Language           | Java 21                                 |
| Service Discovery  | Spring Cloud Netflix Eureka             |
| API Gateway        | Spring Cloud Gateway                    |
| Config Management  | Spring Cloud Config Server              |
| Messaging          | Apache Kafka                            |
| Database           | PostgreSQL (per service), Redis (cache) |
| Security           | Spring Security + JWT                   |
| Resilience         | Resilience4j (Circuit Breaker)          |
| Tracing            | Micrometer + Zipkin                     |
| Containerization   | Docker + Docker Compose                 |
| Build Tool         | Maven                                   |

---

## Implementation Plan

Each phase introduces specific concepts. Complete them in order — each builds on the previous.

---

### Phase 1 — Foundation (Service Discovery & Config) ✅ COMPLETED

**Goal:** Get services to register, discover, and fetch config dynamically.

**Concepts covered:**
- Service Registry pattern (Eureka)
- Centralized configuration (Config Server)
- Spring Cloud dependency management
- Spring profiles (`dev`, `prod`)

**Steps:**
1. ✅ Create parent `pom.xml` with Spring Boot 3.x + Spring Cloud BOM
2. ✅ Build `eureka-server` — `@EnableEurekaServer`, `application.yml`
3. ✅ Build `config-server` — `@EnableConfigServer`, native (local) config repo
4. ✅ Build `user-service` (minimal) — registers with Eureka, fetches config from Config Server
5. ✅ Verified: Eureka dashboard at `http://localhost:8761` — `USER-SERVICE` appears
6. ✅ Verified: Changed property in `config-repo/user-service.yml`, called `POST /actuator/refresh` — value updated without restart

**What was built:**
```
config-repo/
├── user-service.yml          ← default config (port 8081, app.greeting, app.feature-flag)
└── user-service-dev.yml      ← dev profile overrides (feature-flag=true)

eureka-server/                port 8761
config-server/                port 8888  (native backend → config-repo/)
user-service/                 port 8081
  └── GreetingController      GET /api/users/greeting  — reads app.greeting via @Value + @RefreshScope
```

**Key implementation notes:**
- Java 17 used (not 21) — match to your installed JDK
- Config Server uses `spring.profiles.active=native` to read from local filesystem
- `user-service` connects to Config Server via `spring.config.import=optional:configserver:http://localhost:8888`
- `@RefreshScope` on controller + `POST /actuator/refresh` = live config reload without restart
- Start order matters: **Eureka → Config Server → user-service**

**Key interview questions this covers:**
- What is service discovery and why do we need it?
- How does a service register with Eureka?
- What is the difference between client-side and server-side discovery?
- How does Spring Cloud Config Server work?
- What is `@RefreshScope` and how does live config refresh work?

---

### Phase 2 — API Gateway & Routing ✅ COMPLETED

**Goal:** All external traffic enters through one gateway; services remain unreachable directly.

**Concepts covered:**
- API Gateway pattern
- Dynamic routing via Eureka (load-balanced routes)
- Gateway filters (pre/post)
- Rate limiting

**Steps:**
1. ✅ Build `api-gateway` — Spring Cloud Gateway + Eureka client
2. ✅ Configure routes in `application.yml` using `lb://user-service` URIs
3. ✅ Add a global pre-filter to log request method + path
4. ✅ Add a post-filter to log response status
5. ✅ Add rate limiting using Redis + `RequestRateLimiter` filter
6. ✅ Test: All requests go through `localhost:8080/api/users/**`

**What was built:**
```
api-gateway/                        port 8080
  ├── ApiGatewayApplication.java    @SpringBootApplication (WebFlux/Netty, NOT Tomcat)
  ├── filter/
  │   └── LoggingFilter.java        GlobalFilter — pre-logs method+path, post-logs status
  └── config/
      └── RateLimiterConfig.java    RedisRateLimiter(10 req/s, burst 20) + IP KeyResolver
```

**Key implementation notes:**
- Spring Cloud Gateway is WebFlux-based — do NOT add `spring-boot-starter-web` (conflict)
- `lb://user-service` = Eureka lookup + Round Robin client-side load balancing
- `GlobalFilter` = applied to ALL routes automatically; `GatewayFilter` = per-route only
- Pre-filter: code before `chain.filter(exchange)`; Post-filter: `.then(Mono.fromRunnable(...))`
- Rate limiting uses token bucket via Redis — start Redis first: `docker run -d -p 6379:6379 redis:alpine`
- `key-resolver: "#{@ipKeyResolver}"` — SpEL expression wiring the KeyResolver bean to the route
- Start order: **Eureka → Config Server → user-service → Redis → api-gateway**
- Inspect live routes: `GET http://localhost:8080/actuator/gateway/routes`

**Key interview questions this covers:**
- What does an API Gateway do?
- What is the difference between `Zuul` and `Spring Cloud Gateway`?
- How do you implement rate limiting in a gateway?
- What is a filter chain in the context of a gateway?

**Answers to interview questions are in:** `api-gateway/src/main/java/com/microservices/gateway/ApiGatewayApplication.java`

---

### Phase 3 — Inter-Service Communication (Sync) ✅ COMPLETED

**Goal:** Services call each other synchronously using `OpenFeign` with load balancing.

**Concepts covered:**
- Feign client (declarative HTTP client)
- Client-side load balancing (Spring Cloud LoadBalancer)
- Service-to-service auth headers propagation

**Steps:**
1. ✅ Add `order-service` and `product-service` with basic CRUD
2. ✅ In `order-service`, add a Feign client interface to call `product-service` (check stock)
3. ✅ In `order-service`, add a Feign client to call `user-service` (validate user)
4. ✅ Use Feign `RequestInterceptor` to forward the JWT token to downstream services
5. ✅ Test: Create an order → gateway → order-service → product-service + user-service

**What was built:**
```
user-service/
  model/User.java                   record (id, name, email)
  controller/UserController.java    GET /api/users/{id}  ← called by order-service

product-service/              port 8083
  model/Product.java                (id, name, price, stock)
  service/ProductService.java       in-memory store, thread-safe stock reduction
  controller/ProductController.java GET /api/products/{id}
                                    PUT /api/products/{id}/reduce-stock?quantity=N

order-service/                port 8082
  client/UserServiceClient.java     @FeignClient(name="user-service")
  client/ProductServiceClient.java  @FeignClient(name="product-service")
  client/dto/UserResponse.java      local DTO (Tolerant Reader pattern)
  client/dto/ProductResponse.java   local DTO
  interceptor/FeignClientInterceptor.java  RequestInterceptor — forwards JWT + X-Correlation-Id
  service/OrderService.java         orchestrates: validate user → check stock → reduce stock → save
  controller/OrderController.java   POST /api/orders, GET /api/orders/{id}
```

**Key implementation notes:**
- `@EnableFeignClients` on `OrderServiceApplication` scans for `@FeignClient` interfaces
- `@FeignClient(name="user-service")` name MUST match `spring.application.name` in target service
- `FeignClientInterceptor` uses `RequestContextHolder` (ThreadLocal) to read current request — does NOT work in reactive/WebFlux
- Local DTOs (UserResponse, ProductResponse) decouple services — Tolerant Reader pattern
- `FeignException.NotFound` is thrown when downstream returns 404 — catch it explicitly
- In-memory data: users `u1`/`u2`, products `p1`/`p2`/`p3` (p3 has only 5 stock for testing)
- Start order: **Eureka → user-service → product-service → order-service** (gateway optional)

**Key interview questions this covers:**
- What is a Feign client?
- How does load balancing work with Feign + Eureka?
- How do you propagate security context (JWT) across service calls?
- What are the drawbacks of synchronous inter-service communication?

**Answers to interview questions are in:** `order-service/src/main/java/com/microservices/order/OrderServiceApplication.java`

---

### Phase 4 — Resilience (Circuit Breaker & Retry) ✅ COMPLETED

**Goal:** Services degrade gracefully when downstream services are unavailable.

**Concepts covered:**
- Circuit Breaker pattern (Resilience4j)
- Retry pattern
- Bulkhead pattern
- Fallback methods

**Steps:**
1. ✅ Add `resilience4j` + `spring-boot-starter-aop` to `order-service`
2. ✅ `@CircuitBreaker` + `@Retry` on product-service calls; `@Retry` on user-service calls
3. ✅ Fallback methods that propagate business errors and throw `ServiceUnavailableException` for infra failures
4. ✅ Full threshold config in `application.yml` (failure rate, window size, wait duration, slow-call threshold)
5. ✅ `@Bulkhead(SEMAPHORE)` on `reduceStock` — caps concurrent calls
6. ✅ Test: shut down `product-service` → watch circuit open; restart → watch circuit close
7. ✅ `GET /actuator/health` shows `CLOSED`/`OPEN`/`HALF_OPEN` per circuit breaker

**What was built:**
```
order-service/resilience/
  ServiceUnavailableException.java  @ResponseStatus(503) — thrown by all fallbacks
  ProductServiceGateway.java        @CircuitBreaker + @Retry on getProduct()
                                    @CircuitBreaker + @Bulkhead on reduceStock()
                                    fallback methods for each
  UserServiceGateway.java           @Retry(maxAttempts=3) on getUser()
                                    fallback propagates business errors as-is
```

**Key implementation notes:**
- AOP self-invocation problem: `@CircuitBreaker` only works when called through the Spring proxy.
  Putting annotations in a separate `@Service` class (Gateway) solves this.
- Stacking order: `@Bulkhead` → `@CircuitBreaker` → `@Retry` → actual Feign call (outer to inner)
- `ignore-exceptions: [IllegalArgumentException]` — business errors (404, bad stock) don't trip the circuit
- Fallback method signature: **same params as original + `Throwable` at end** (must match exactly)
- `sliding-window-size: 10` + `minimum-number-of-calls: 5` — circuit won't open on the very first failure
- `automatic-transition-from-open-to-half-open-enabled: true` — no manual intervention needed
- Check CB state: `GET http://localhost:8082/actuator/health`
- Check CB metrics: `GET http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state`

**Test procedure:**
```bash
# 1. Confirm normal operation
POST http://localhost:8082/api/orders  { "userId":"u1","productId":"p1","quantity":1 }

# 2. Stop product-service — send 5+ requests → watch circuit OPEN in logs
# 3. All requests now get 503 immediately (no Feign call made)
# 4. Restart product-service — wait 10s → circuit moves to HALF-OPEN → then CLOSED
# 5. GET /actuator/health to see CB state at any point
```

**Key interview questions this covers:**
- What is the Circuit Breaker pattern? What are its states?
- What is the difference between a retry and a circuit breaker?
- What is the Bulkhead pattern?
- How do you configure Resilience4j in Spring Boot?

**Answers to interview questions are in:** `order-service/src/main/java/com/microservices/order/OrderServiceApplication.java`

---

### Phase 5 — Async Messaging (Kafka)

**Goal:** Services communicate asynchronously through events, decoupling producers from consumers.

**Concepts covered:**
- Event-driven architecture
- Kafka producer/consumer
- Topic partitioning
- At-least-once vs exactly-once delivery
- Saga pattern (choreography)

**Steps:**
1. Add Kafka to `docker-compose.yml` (Zookeeper + Kafka + Kafka UI)
2. In `order-service`, publish an `OrderCreatedEvent` to Kafka topic `order-events` after order creation
3. Build `notification-service` — consumes `order-events` and logs/sends a notification
4. Add `product-service` consumer — on `OrderCreatedEvent`, reduce stock quantity
5. Implement a simple Saga: if stock reduction fails, publish `OrderFailedEvent`; `order-service` consumes it and rolls back
6. Test with Kafka UI at `localhost:8085`

**Key interview questions this covers:**
- What is event-driven architecture?
- What is the difference between synchronous and asynchronous communication?
- What is the Saga pattern? Choreography vs Orchestration?
- How do you handle message ordering in Kafka?
- What is consumer group and partition assignment?

---

### Phase 6 — Security (JWT + OAuth2)

**Goal:** Secure all services; gateway authenticates, downstream services authorize.

**Concepts covered:**
- JWT structure and validation
- Gateway as authentication entry point
- Role-based access control (RBAC)
- Spring Security filter chain

**Steps:**
1. In `user-service`, add login endpoint — validates credentials, returns a signed JWT
2. Add JWT utility class (sign with private key, validate with public key — RS256)
3. In `api-gateway`, add a global filter: extract JWT from `Authorization` header, validate it; reject with 401 if invalid
4. Pass claims (userId, roles) as headers to downstream services (`X-User-Id`, `X-User-Role`)
5. In each service, add Spring Security config to read roles from headers and enforce `@PreAuthorize`
6. Test: Call without token → 401; call with wrong role → 403; call with correct role → 200

**Key interview questions this covers:**
- How does JWT work? What are its parts?
- Where should JWT validation happen in a microservices architecture?
- What is the difference between authentication and authorization?
- How do you implement RBAC with Spring Security?

---

### Phase 7 — Distributed Tracing & Observability

**Goal:** Trace a single request as it flows through multiple services; centralize logs and metrics.

**Concepts covered:**
- Distributed tracing (Micrometer Tracing + Zipkin)
- Correlation ID / Trace ID / Span ID
- Structured logging (MDC)
- Actuator metrics + Prometheus + Grafana

**Steps:**
1. Add Micrometer Tracing + Zipkin reporter to all services
2. Add Zipkin to `docker-compose.yml` (port 9411)
3. Verify trace IDs appear in all logs when a request flows through gateway → order-service → product-service
4. Add Prometheus scrape endpoint (`/actuator/prometheus`) to all services
5. Add Prometheus + Grafana to `docker-compose.yml`
6. Import Spring Boot dashboard in Grafana; add a custom alert for high error rate
7. Add a `X-Correlation-Id` header in the gateway filter and propagate it via MDC

**Key interview questions this covers:**
- What is distributed tracing? Why is it needed?
- What is the difference between a Trace ID and a Span ID?
- How do you correlate logs across services?
- What is the difference between metrics, logs, and traces (the three pillars)?

---

### Phase 8 — Persistence & Caching

**Goal:** Each service owns its data (Database per Service pattern); hot data is cached.

**Concepts covered:**
- Database per Service pattern
- Redis caching (`@Cacheable`, `@CacheEvict`)
- Flyway database migrations
- Optimistic locking (versioning)

**Steps:**
1. Add PostgreSQL (separate DB per service) to `docker-compose.yml`
2. Add Flyway migrations to each service (`src/main/resources/db/migration/V1__init.sql`)
3. Add Redis to `docker-compose.yml`
4. In `product-service`, cache product lookups with `@Cacheable("products")` using Redis
5. On update, evict cache with `@CacheEvict`
6. Add `@Version` field to `Order` entity for optimistic locking
7. Test: Read product twice — second call should hit cache (log shows no DB query)

**Key interview questions this covers:**
- What is the Database per Service pattern? Why is it important?
- What are the caching strategies (cache-aside, write-through, write-behind)?
- How does Flyway handle database migrations?
- What is optimistic vs pessimistic locking?

---

### Phase 9 — Containerization & Docker Compose

**Goal:** Run the entire stack locally with a single `docker-compose up`.

**Concepts covered:**
- Multi-stage Docker builds
- Docker networking (service names as hostnames)
- Health checks and `depends_on`
- Environment variable configuration

**Steps:**
1. Write `Dockerfile` for each service using multi-stage build (builder + slim JRE image)
2. Write `docker-compose.yml` with all services, infra (Postgres, Redis, Kafka, Zipkin, Prometheus, Grafana)
3. Add `healthcheck` to each container
4. Use `depends_on: condition: service_healthy` so services start only after infra is ready
5. Externalize all secrets as environment variables (no hardcoded passwords)
6. Test: `docker-compose up --build` → all services green in Eureka dashboard

**Key interview questions this covers:**
- What is a multi-stage Docker build and why use it?
- How do containers communicate in Docker Compose?
- What is the difference between `CMD` and `ENTRYPOINT`?
- How do you handle service startup order in Docker Compose?

---

### Phase 10 — Advanced Patterns

**Goal:** Add production-grade patterns that come up frequently in senior-level interviews.

**Concepts covered:**
- CQRS (Command Query Responsibility Segregation)
- Event Sourcing (basic)
- Idempotency keys (prevent duplicate order creation)
- Outbox Pattern (guaranteed event delivery)
- API versioning

**Steps:**
1. **Idempotency:** Accept `Idempotency-Key` header on `POST /orders`; store in Redis; return cached response on duplicate
2. **Outbox Pattern:** Instead of publishing Kafka events directly, write to an `outbox` table in the same DB transaction; a scheduler reads and publishes, then marks as processed
3. **CQRS:** Split `product-service` into write model (Postgres) and read model (denormalized view in Redis)
4. **API Versioning:** Add `/v1/` and `/v2/` endpoints in `user-service`; route based on version in the gateway
5. **Health aggregation:** Build a composite health check that reports `DOWN` if Kafka or DB is unavailable

**Key interview questions this covers:**
- What is CQRS? When would you use it?
- What is the Outbox Pattern and why is it needed?
- How do you ensure idempotency in a distributed system?
- What is eventual consistency?

---

## Running the Project

```bash
# Start infrastructure only
docker-compose up -d postgres redis kafka zookeeper zipkin

# Start all services in dev mode (from each service directory)
mvn spring-boot:run

# Or start everything with Docker
docker-compose up --build
```

**Service URLs:**
| Service           | URL                          |
|-------------------|------------------------------|
| Eureka Dashboard  | http://localhost:8761         |
| API Gateway       | http://localhost:8080         |
| Config Server     | http://localhost:8888         |
| Zipkin UI         | http://localhost:9411         |
| Kafka UI          | http://localhost:8085         |
| Prometheus        | http://localhost:9090         |
| Grafana           | http://localhost:3000         |

---

## Interview Cheat Sheet

| Pattern / Concept        | Where implemented             |
|--------------------------|-------------------------------|
| Service Discovery        | eureka-server + all clients   |
| API Gateway              | api-gateway                   |
| Circuit Breaker          | order-service → product-service|
| Event-Driven / Saga      | order-service, notification-service |
| JWT Auth                 | user-service, api-gateway     |
| Distributed Tracing      | All services + Zipkin         |
| Database per Service     | Each service has its own DB   |
| Redis Caching            | product-service               |
| Outbox Pattern           | order-service                 |
| Idempotency              | order-service                 |
| CQRS                     | product-service               |
| Docker / Containerization| docker-compose.yml            |
