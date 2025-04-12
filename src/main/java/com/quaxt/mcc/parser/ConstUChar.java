package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record ConstUChar(int i) implements Constant {
    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return "(uchar)" + String.valueOf((char) i);
    }


    public long toLong() {
        return i;
    }
}
