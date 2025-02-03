package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.INT;

public record ConstInt(int i) implements Constant {
    @Override
    public Type type() {
        return INT;
    }
}
