package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.DOUBLE;

public record ConstDouble(double d) implements Constant {
    @Override
    public Type type() {
        return DOUBLE;
    }

    public int toInt() {
        return (int)d;
    }
}
