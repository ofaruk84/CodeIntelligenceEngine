package com.example.commerce;

public class NestedTypes {
    public Worker create(PaymentService payments) { return new Worker(payments); }
    public static class Worker {
        private final PaymentService payments;
        public Worker(PaymentService payments) { this.payments = payments; }
        public void run(String orderId) { payments.pay(orderId); }
        public void run(int count) {}
    }
}
