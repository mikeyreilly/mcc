package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record UIntInit(int i) implements StaticInit, Constant {
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


