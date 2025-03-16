package com.quaxt.mcc.semantic;

import com.quaxt.mcc.StaticInit;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record Pointer(Type referenced) implements Type {
    public Pointer(Type referenced) {
        this.referenced = referenced;
    }

    public StaticInit zero() {
        return ULONG.zero();
    }

    @Override
    public boolean looseEquals(Type other) {
        return other instanceof Pointer(Type otherReferenced) && referenced.looseEquals(otherReferenced);
    }
}
