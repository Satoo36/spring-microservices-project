package com.microservices.order.client.dto;

/*
 * DTO that mirrors the Product returned by product-service's GET /api/products/{id}.
 * Jackson auto-maps JSON fields by name (field name must match JSON key).
 */
public class ProductResponse {
    private String id;
    private String name;
    private double price;
    private int stock;

    public String getId()    { return id; }
    public String getName()  { return name; }
    public double getPrice() { return price; }
    public int getStock()    { return stock; }

    public void setId(String id)       { this.id = id; }
    public void setName(String name)   { this.name = name; }
    public void setPrice(double price) { this.price = price; }
    public void setStock(int stock)    { this.stock = stock; }
}
