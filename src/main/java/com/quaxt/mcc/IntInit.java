package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.INT;

public record IntInit(int i) implements StaticInit, Constant {
    @Override
    public Type type() {
        return INT;
    }

    public long toLong() {
        return i;
    }
}
