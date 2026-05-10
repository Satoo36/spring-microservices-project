package com.microservices.order.model;

import java.time.LocalDateTime;

/*
 * Order entity — Phase 8 will add @Entity + @Id for PostgreSQL persistence.
 * Status values: CONFIRMED (stock reserved), FAILED (user/stock check failed).
 */
public class Order {

    private final String id;
    private final String userId;
    private final String productId;
    private final int quantity;
    private final double totalPrice;
    private final String status;
    private final LocalDateTime createdAt;

    public Order(String id, String userId, String productId,
                 int quantity, double totalPrice, String status) {
        this.id         = id;
        this.userId     = userId;
        this.productId  = productId;
        this.quantity   = quantity;
        this.totalPrice = totalPrice;
        this.status     = status;
        this.createdAt  = LocalDateTime.now();
    }

    public String getId()            { return id; }
    public String getUserId()        { return userId; }
    public String getProductId()     { return productId; }
    public int getQuantity()         { return quantity; }
    public double getTotalPrice()    { return totalPrice; }
    public String getStatus()        { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
