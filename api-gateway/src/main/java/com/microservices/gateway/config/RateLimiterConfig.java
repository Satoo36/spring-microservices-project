package com.microservices.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/*
 * RATE LIMITING — Token Bucket Algorithm via Redis
 * ================================================
 *
 * ALGORITHM: Token Bucket
 *   - Redis maintains a bucket of tokens per "key" (e.g., per IP).
 *   - Tokens are added at `replenishRate` every second.
 *   - Each incoming request consumes `requestedTokens` (default: 1).
 *   - If the bucket has enough tokens → request is forwarded (200).
 *   - If the bucket is empty → gateway rejects with HTTP 429 Too Many Requests.
 *   - `burstCapacity` = max tokens the bucket can ever hold.
 *     Allows short traffic spikes above the steady-state rate.
 *
 * EXAMPLE (replenishRate=10, burstCapacity=20):
 *   - Steady state: 10 requests/sec sustained indefinitely.
 *   - After an idle second: bucket fills to 20, so a burst of 20 is allowed.
 *   - Beyond 20 in a single second → 429.
 *
 * WHY REDIS?
 *   - Rate limiting state must be shared across multiple gateway instances
 *     (horizontal scaling). Redis provides atomic operations (EVAL/Lua script)
 *     to safely read-and-decrement the token count across instances.
 *
 * SETUP (required before starting gateway):
 *   docker run -d -p 6379:6379 redis:alpine
 *
 * KeyResolver: determines the "bucket key" from the request.
 *   - By IP    → one limit per client IP (shown here, good for public APIs)
 *   - By User  → extract userId from JWT claim (good for authenticated APIs)
 *   - By API key → extract from header (good for partner APIs)
 */
@Configuration
public class RateLimiterConfig {

    /*
     * RedisRateLimiter(replenishRate, burstCapacity, requestedTokens)
     *
     * replenishRate   = 10  → add 10 tokens/second to the bucket
     * burstCapacity   = 20  → bucket can hold at most 20 tokens
     * requestedTokens = 1   → each request costs 1 token
     *
     * This bean is referenced in application.yml via the route filter config.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    /*
     * KeyResolver: extracts the "bucket key" from the incoming request.
     *
     * Here we use the client's remote IP address so each IP gets its own bucket.
     * Returns Mono<String> because everything in WebFlux is reactive/async.
     *
     * Bean name "ipKeyResolver" matches the #{@ipKeyResolver} SpEL expression
     * used in application.yml to wire this resolver to the route's rate limiter.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "UNKNOWN";
            return Mono.just(ip);
        };
    }
}
