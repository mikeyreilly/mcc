package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record Alignof(Exp exp) implements Exp {
    @Override
    public Type type() {
        return ULONG;
    }
}
