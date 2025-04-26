package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.LONG;

public record LongInit(long l) implements StaticInit, Constant {
    @Override
    public Type type() {
        return LONG;
    }


    public long toLong() {
        return l;
    }
}
