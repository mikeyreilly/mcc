package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record ConstULong(long l) implements Constant {
    @Override
    public Type type() {
        return ULONG;
    }

    @Override
    public String toString() {
        return "ConstULong[l=" + Long.toUnsignedString(l) + "]";
    }


    public int toInt() {
        return (int)l;
    }
}
