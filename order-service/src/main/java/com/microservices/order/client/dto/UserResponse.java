package com.microservices.order.client.dto;

/*
 * DTO that mirrors the User record returned by user-service's GET /api/users/{id}.
 * Feign deserializes the JSON response body into this class automatically (via Jackson).
 *
 * WHY a separate DTO instead of sharing the User class directly?
 * ---------------------------------------------------------------
 * Each microservice should be INDEPENDENTLY deployable.
 * If order-service imported user-service's User class, a change in user-service
 * (e.g., adding a field) would force order-service to recompile and redeploy.
 * A local DTO decouples the two — order-service only maps fields it cares about.
 * This is the "Tolerant Reader" pattern.
 */
public class UserResponse {
    private String id;
    private String name;
    private String email;

    public String getId()    { return id; }
    public String getName()  { return name; }
    public String getEmail() { return email; }

    public void setId(String id)       { this.id = id; }
    public void setName(String name)   { this.name = name; }
    public void setEmail(String email) { this.email = email; }
}
