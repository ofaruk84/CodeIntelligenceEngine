package com.example.commerce;

public class CycleA {
    private final CycleB next;
    public CycleA(CycleB next) { this.next = next; }
    public void run() { next.run(); }
}
