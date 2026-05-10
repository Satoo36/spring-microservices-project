package com.microservices.order.model;

/*
 * Request body for POST /api/orders.
 * Example JSON:
 * {
 *   "userId":    "u1",
 *   "productId": "p1",
 *   "quantity":  2
 * }
 */
public class CreateOrderRequest {
    private String userId;
    private String productId;
    private int quantity;

    public String getUserId()    { return userId; }
    public String getProductId() { return productId; }
    public int getQuantity()     { return quantity; }

    public void setUserId(String userId)       { this.userId = userId; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setQuantity(int quantity)      { this.quantity = quantity; }
}
