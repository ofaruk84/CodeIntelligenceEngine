package com.example.commerce;

public class RetryPaymentJob {
    private final PaymentService payments;
    public RetryPaymentJob(PaymentService payments) { this.payments = payments; }
    public void retry(String orderId) { payments.pay(orderId, 2); }
}
