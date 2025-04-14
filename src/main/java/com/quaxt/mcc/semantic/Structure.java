package com.quaxt.mcc.semantic;

public record Structure(String tag) implements Type {
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }
}
