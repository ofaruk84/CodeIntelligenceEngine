package com.example.commerce;

import missing.vendor.ExternalGateway;
public class UnresolvedClient {
    private final ExternalGateway gateway;
    public UnresolvedClient(ExternalGateway gateway) { this.gateway = gateway; }
    public void send(String orderId) { gateway.deliver(orderId); }
}
