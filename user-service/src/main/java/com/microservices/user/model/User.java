package com.microservices.user.model;

/*
 * Simple User record — Phase 3 adds this so order-service can validate users.
 * Java 16+ record: immutable data class with auto-generated constructor,
 * getters, equals, hashCode, and toString.
 */
public record User(String id, String name, String email) {
}
