package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record ConstUInt(int i) implements Constant {
    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return "ConstUInt[i=" + Integer.toUnsignedString(i) + "]";
    }


    public long toLong() {
        return i;
    }
}
