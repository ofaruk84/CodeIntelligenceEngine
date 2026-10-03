package com.example.commerce;

public class CycleC {
    private final CycleA next;
    public CycleC(CycleA next) { this.next = next; }
    public void run() { next.run(); }
}
