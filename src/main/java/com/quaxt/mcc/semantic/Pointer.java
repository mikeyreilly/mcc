package com.quaxt.mcc.semantic;

public record Pointer(Type referenced) implements Type {
    public Pointer(Type referenced) {
        this.referenced = referenced;
    }
}
