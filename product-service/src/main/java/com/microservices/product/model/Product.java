package com.microservices.product.model;

/*
 * Product — mutable class (not a record) because stock changes on each order.
 * Phase 8 will replace this with a JPA @Entity backed by PostgreSQL.
 */
public class Product {

    private final String id;
    private final String name;
    private final double price;
    private int stock; // mutable — decremented when orders are placed

    public Product(String id, String name, double price, int stock) {
        this.id    = id;
        this.name  = name;
        this.price = price;
        this.stock = stock;
    }

    public String getId()    { return id; }
    public String getName()  { return name; }
    public double getPrice() { return price; }
    public int getStock()    { return stock; }

    public void setStock(int stock) { this.stock = stock; }
}
