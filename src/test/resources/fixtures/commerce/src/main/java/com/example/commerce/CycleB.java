package com.example.commerce;

public class CycleB {
    private final CycleC next;
    public CycleB(CycleC next) { this.next = next; }
    public void run() { next.run(); }
}
