package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.semantic.Primitive.UINT;

public record ConstChar(byte i) implements Constant {
    final static ConstChar ZERO = new ConstChar((byte) 0);

    public static ValIr zero() {
        return ZERO;
    }

    @Override
    public Type type() {
        return UINT;
    }

    @Override
    public String toString() {
        return String.valueOf((char) i);
    }


    public int toInt() {
        return i;
    }
}
