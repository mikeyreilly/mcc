package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.semantic.Primitive.CHAR;

public record ConstChar(byte i) implements Constant {
    final static ConstChar ZERO = new ConstChar((byte) 0);

    public static ValIr zero() {
        return ZERO;
    }

    @Override
    public Type type() {
        return CHAR;
    }

    @Override
    public String toString() {
        return String.valueOf((char) i);
    }


    public long toLong() {
        return i;
    }
}
