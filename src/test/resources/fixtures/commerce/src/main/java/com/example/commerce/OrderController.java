package com.example.commerce;

public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    public void submit(String orderId) { orders.place(orderId); }
}
