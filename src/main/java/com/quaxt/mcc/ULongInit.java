package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record ULongInit(long l) implements StaticInit, Constant {

    @Override
    public Type type() {
        return ULONG;
    }

    @Override
    public String toString() {
        return "ConstULong[l=" + Long.toUnsignedString(l) + "]";
    }


    public long toLong() {
        return l;
    }
}
