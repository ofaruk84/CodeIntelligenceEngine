package com.example.commerce;

public class PaymentService {
    public PaymentService() { this(0); }
    public PaymentService(int retries) { super(); }
    public void pay(String orderId) { audit(orderId); }
    public void pay(String orderId, int attempts) { pay(orderId); }
    private void audit(String orderId) {}
}
