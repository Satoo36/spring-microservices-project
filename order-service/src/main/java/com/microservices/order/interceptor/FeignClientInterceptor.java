package com.microservices.order.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/*
 * FEIGN REQUEST INTERCEPTOR — JWT Propagation
 * =============================================
 *
 * PROBLEM:
 *   When a client calls order-service, it sends:
 *     Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
 *
 *   When order-service internally calls product-service via Feign,
 *   Feign creates a BRAND NEW HTTP request — the Authorization header
 *   is NOT automatically forwarded. product-service has no way to
 *   authenticate the call or know which user triggered it.
 *
 * SOLUTION — RequestInterceptor:
 *   Feign calls apply(RequestTemplate) on EVERY outgoing request BEFORE
 *   it is sent. Here we extract the JWT from the current incoming request
 *   and add it to the outgoing Feign request template.
 *
 * HOW RequestContextHolder WORKS:
 *   Spring stores the current HttpServletRequest in a ThreadLocal (per-thread).
 *   RequestContextHolder.getRequestAttributes() retrieves it from the current thread.
 *   This works because servlet containers use one thread per request (thread-per-request model).
 *
 *   WARNING: This does NOT work in reactive/WebFlux contexts (no ThreadLocal).
 *   In WebFlux, use Reactor's context propagation instead.
 *
 * WHAT GETS FORWARDED:
 *   - Authorization: Bearer <jwt>   → downstream services can validate the token
 *   We also forward X-Correlation-Id (added in Phase 7 for distributed tracing).
 */
@Component
public class FeignClientInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignClientInterceptor.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public void apply(RequestTemplate template) {
        // Retrieve the current HTTP request from the thread-local storage
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            // Can happen in async contexts or scheduled jobs — skip silently
            return;
        }

        HttpServletRequest request = attributes.getRequest();

        // Forward JWT token to downstream services
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && !authHeader.isBlank()) {
            template.header(AUTHORIZATION_HEADER, authHeader);
            log.debug("Forwarding Authorization header to downstream service");
        }

        // Forward correlation ID for distributed tracing (Phase 7 will add this)
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            template.header(CORRELATION_ID_HEADER, correlationId);
        }
    }
}
