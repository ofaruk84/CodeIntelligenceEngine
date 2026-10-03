package com.example.commerce;

public class InventoryService {
    private final InventoryRepository repository;
    public InventoryService(InventoryRepository repository) { this.repository = repository; }
    public void reserve(String orderId) { repository.save(orderId); }
}
