package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.LONG;

public record ConstLong(long l) implements Constant {
    @Override
    public Type type() {
        return LONG;
    }
}
