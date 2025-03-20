package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record ConstChar(int i) implements Constant {
    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return String.valueOf((char)i);
    }


    public int toInt() {
        return i;
    }
}
