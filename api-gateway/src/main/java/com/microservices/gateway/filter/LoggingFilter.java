package com.microservices.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/*
 * GLOBAL LOGGING FILTER — Pre + Post phases
 * ==========================================
 *
 * GlobalFilter: Spring Cloud Gateway auto-applies this to EVERY route.
 * No per-route declaration needed — just register it as a @Component.
 *
 * Ordered interface: controls execution order in the filter chain.
 *   HIGHEST_PRECEDENCE (Integer.MIN_VALUE) = runs first (pre), last (post).
 *   LOWEST_PRECEDENCE  (Integer.MAX_VALUE) = runs last  (pre), first (post).
 *
 * Why reactive (Mono<Void>)?
 *   Spring Cloud Gateway is built on Spring WebFlux (Project Reactor).
 *   Everything is non-blocking. filter() must return Mono<Void>
 *   (a promise that completes when the filter chain is done).
 *
 * PRE vs POST explained:
 *   - Code BEFORE chain.filter(exchange)  = pre-filter  (runs before proxy call)
 *   - .then(Mono.fromRunnable(...))       = post-filter (runs after proxy returns)
 *
 * ServerWebExchange: the main context object in WebFlux, holding:
 *   - ServerHttpRequest  (incoming request)
 *   - ServerHttpResponse (outgoing response)
 *   - Attributes         (shared state across filters)
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // ── PRE-FILTER ──────────────────────────────────────────────────────────
        // Runs BEFORE the request is forwarded to the downstream service.
        log.info("[PRE ] {} {} | requestId={}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getId());

        // chain.filter(exchange) passes control to the next filter in the chain.
        // When all filters have run, the gateway proxies to the downstream service.
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {

            // ── POST-FILTER ──────────────────────────────────────────────────────
            // .then() runs AFTER chain.filter() completes (i.e., after the
            // downstream service responds). This is the post-filter phase.
            ServerHttpResponse response = exchange.getResponse();
            log.info("[POST] {} {} | status={}",
                    request.getMethod(),
                    request.getURI().getPath(),
                    response.getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        // Run this filter first so every request is logged before any other
        // processing (auth, rate limiting, etc.) can short-circuit it.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
