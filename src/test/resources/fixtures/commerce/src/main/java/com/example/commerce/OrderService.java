package com.example.commerce;

import java.util.List;
import static java.util.Collections.emptyList;
public class OrderService {
    private final PaymentService payments;
    private final InventoryService inventory;
    private final NotificationService notifications;
    public OrderService(PaymentService payments, InventoryService inventory, NotificationService notifications) {
        this.payments = payments;
        this.inventory = inventory;
        this.notifications = notifications;
    }
    public void place(String orderId) {
        validate(orderId);
        payments.pay(orderId);
        payments.pay(orderId);
        inventory.reserve(orderId);
        notifications.confirm(orderId);
    }
    private void validate(String orderId) { List<String> pending = emptyList(); }
}
