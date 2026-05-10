package com.microservices.user.controller;

import com.microservices.user.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/*
 * UserController — Phase 3 addition.
 * Exposes GET /api/users/{id} so order-service can validate a user via Feign.
 *
 * In-memory store simulates a database (Phase 8 will replace with PostgreSQL).
 * Pre-seeded with 2 users so Feign calls can be tested immediately.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    // Immutable in-memory "database" — keyed by user ID
    private static final Map<String, User> USERS = Map.of(
            "u1", new User("u1", "Alice Smith",   "alice@example.com"),
            "u2", new User("u2", "Bob Johnson",   "bob@example.com")
    );

    /*
     * Called by order-service via Feign to check the user exists before placing an order.
     * Returns 200 + User if found, 404 if not.
     *
     * ResponseEntity<User> lets us control the HTTP status code:
     *   - ResponseEntity.ok(user)       → 200 OK
     *   - ResponseEntity.notFound()...  → 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User user = USERS.get(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }
}